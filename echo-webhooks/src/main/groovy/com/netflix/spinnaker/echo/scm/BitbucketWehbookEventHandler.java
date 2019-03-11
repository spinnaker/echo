/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.scm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.scm.bitbucket.BitbucketCloudEvent;
import com.netflix.spinnaker.echo.scm.bitbucket.BitbucketServerPrMergedEvent;
import com.netflix.spinnaker.echo.scm.bitbucket.BitbucketServerRepoRefsChangedEvent;
import com.netflix.spinnaker.echo.scm.bitbucket.BitbucketWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@Slf4j
public class BitbucketWehbookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public BitbucketWehbookEventHandler() {
    this.objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public boolean handles(String source) {
    return "bitbucket".equals(source);
  }

  public boolean shouldSendEvent(Event event) {
    if (event.rawContent.isEmpty()) {
      return false;
    }

    if (event.content.containsKey("hash") &&
       event.content.get("hash").toString().startsWith("000000000")) {
      return false;
    }

    return true;
  }

  public void handle(Event event, Map postedEvent) {
    if (event.rawContent.isEmpty() || !event.content.containsKey("event_type")) {
      log.info("Handling Bitbucket Server ping.");
      return;
    }

    BitbucketWebhookEvent webhookEvent = null;
    if (looksLikeBitbucketCloud(event)) {
      webhookEvent = objectMapper.convertValue(event.content, BitbucketCloudEvent.class);
    } else if (lookLikeBitbucketServer(event)) {
      String eventType = event.content.get("event_type").toString();
      if (eventType == "repo:refs_changed") {
        webhookEvent = objectMapper.convertValue(event.content, BitbucketServerRepoRefsChangedEvent.class);
      } else if (eventType == "pr:merged") {
        webhookEvent = objectMapper.convertValue(event.content, BitbucketServerPrMergedEvent.class);
      }
    }

    if (webhookEvent == null) {
      return;
    }

    String fullRepoName = webhookEvent.getFullRepoName(event);
    Map<String, String> results = new HashMap<>();
    results.put("repoProject", webhookEvent.getRepoProject(event, postedEvent));
    results.put("slug", webhookEvent.getSlug(event, postedEvent));
    results.put("hash", webhookEvent.getHash(event, postedEvent));
    results.put("branch", webhookEvent.getBranch(event, postedEvent));
    event.content.putAll(results);

    if (fullRepoName != "") {
      log.info("Webhook event received {} {} {} {} {} {}", kv("type", "git"),
        kv("event_type", event.content.get("event_type").toString()),
        kv("hook_id", event.content.containsKey("hook_id") ? event.content.get("hook_id").toString() : ""),
        kv("repository", fullRepoName),
        kv("request_id", event.content.containsKey("request_id") ? event.content.get("request_id").toString() : ""),
        kv("branch", results.get("branch")));
    } else {
      log.info("Webhook event received {} {} {} {} {}",
        kv("type", "git"),
        kv("event_type", event.content.get("event_type").toString()),
        kv("hook_id", event.content.containsKey("hook_id") ? event.content.get("hook_id").toString() : ""),
        kv("request_id", event.content.containsKey("request_id") ? event.content.get("request_id").toString() : ""),
        kv("branch", results.get("branch").toString()));
    }
  }

  private boolean looksLikeBitbucketCloud(Event event) {
    if (!event.content.containsKey("event_type")) {
      return false;
    }

    String eventType = event.content.get("event_type").toString();
    return (eventType == "repo:push" || eventType == "pullrequest:fulfilled");
  }

  private boolean lookLikeBitbucketServer(Event event) {
    if (!event.content.containsKey("event_type")) {
      return false;
    }

    String eventType = event.content.get("event_type").toString();
    return (eventType == "repo:refs_changed" || eventType == "pr:merged");
  }




}
