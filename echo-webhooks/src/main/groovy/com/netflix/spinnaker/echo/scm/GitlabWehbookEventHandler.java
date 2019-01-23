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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GitlabWehbookEventHandler implements GitWebhookHandler {

  private ObjectMapper objectMapper;

  public GitlabWehbookEventHandler(){
    this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public boolean handles(String source){
    return "gitlab".equals(source);
  };

  public boolean shouldSendEvent(Event event) {
    return true;
  }

  public void handle(Event event, Map postedEvent) {
    GitlabWebhookEvent gitlabWebhookEvent = objectMapper.convertValue(postedEvent, GitlabWebhookEvent.class);
    event.content.put("hash", gitlabWebhookEvent.after);
    event.content.put("branch", gitlabWebhookEvent.ref.replace("refs/heads/", ""));
    event.content.put("repoProject", gitlabWebhookEvent.project.namespace);
    event.content.put("slug", gitlabWebhookEvent.project.name);
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GitlabWebhookEvent {
    String after;
    String ref;
    GitlabProject project;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GitlabProject {
    String name;
    String namespace;
  }
}
