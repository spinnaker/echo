/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import com.netflix.spinnaker.echo.github.GithubService;
import com.netflix.spinnaker.echo.github.GithubStatus;
import com.netflix.spinnaker.echo.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@ConditionalOnProperty("githubStatus.enabled")
@Service
public class GithubNotificationAgent extends AbstractEventNotificationAgent {
  private ImmutableMap<String, String> STATUSES = ImmutableMap.of(
    "starting", "pending",
    "complete", "success",
    "failed", "failure");

  @Override
  public void sendNotifications(
    Map preference,
    final String application,
    final Event event,
    Map config,
    final String status) {
    EventContent content = null;
    try {
      content = new EventContent(event, (String) config.get("type"));
    } catch (FieldNotFoundException e) {
      return;
    }

    String state = STATUSES.get(status);

    String description;
    String context;
    String targetUrl;

    if (config.get("type").equals("stage")) {
      description = String.format("Stage '%s' in pipeline '%s' is %s",
        content.getStageName(),
        content.getPipeline(),
        status);
      context = "Stage";
      targetUrl = String.format("%s/#/applications/%s/executions/details/%s?pipeline=%s&stage=%d",
        getSpinnakerUrl(),
        application,
        content.getExecutionId(),
        content.getPipeline(),
        content.getStageIndex());
    } else if (config.get("type").equals("pipeline")) {
      description = String.format("Pipeline '%s' is %s", content.getPipeline(), status);
      context = "Pipeline";
      targetUrl = String.format("%s/#/applications/%s/executions/details/%s?pipeline=%s",
        getSpinnakerUrl(),
        application,
        content.getExecutionId(),
        content.getPipeline());
    } else {
      return;
    }

    log.info(String.format("Sending Github status check for application: %s", application));

    GithubStatus githubStatus = new GithubStatus(state, targetUrl, description, context);

    try {
      githubService.updateCheck("token " + token, content.getRepo(), content.getSha(), githubStatus);
    } catch (Exception e) {
      log.error(String.format("Failed to send github status for application: '%s' pipeline: '%s', %s",
        application, content.getPipeline(), e));
    }
  }

  @Override
  public String getNotificationType() {
    return "githubStatus";
  }

  public GithubService getGithubService() {
    return githubService;
  }

  public void setGithubService(GithubService githubService) {
    this.githubService = githubService;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Autowired
  private GithubService githubService;
  @Value("${githubStatus.token}")
  private String token;
}
