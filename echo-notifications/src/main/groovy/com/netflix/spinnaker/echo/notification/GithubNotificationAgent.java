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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.echo.exceptions.FieldNotFoundException;
import com.netflix.spinnaker.echo.github.*;
import com.netflix.spinnaker.echo.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
      context = String.format("stage/%s", content.getStageName());
      targetUrl = String.format("%s/#/applications/%s/executions/details/%s?pipeline=%s&stage=%d",
        getSpinnakerUrl(),
        application,
        content.getExecutionId(),
        content.getPipeline(),
        content.getStageIndex());
    } else if (config.get("type").equals("pipeline")) {
      description = String.format("Pipeline '%s' is %s", content.getPipeline(), status);
      context = String.format("pipeline/%s", content.getPipeline());
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

    String realCommit = getRealCommit(content.getRepo(), content.getSha());

    try {
      githubService.updateCheck("token " + token, content.getRepo(), realCommit, githubStatus);
    } catch (Exception e) {
      log.error(String.format("Failed to send github status for application: '%s' pipeline: '%s', %s",
        application, content.getPipeline(), e));
    }
  }

  private boolean commitFromMaster(String repo, String sha) {
    // The /search/commit query only gets commits from the active branch so if a commit is returned that
    // means that it is in the master branch
    String query = String.format("repo:%s+hash:%s", repo, sha);
    Response response = null;
    try {
      response = githubService.searchCommits("token " + token, query);
    } catch (RetrofitError e) {
      System.out.println(e.getResponse().getStatus());
      return false;
    }
    ObjectMapper objectMapper = new ObjectMapper();
    GithubSearchCommits message = null;
    try {
      message = objectMapper.readValue(response.getBody().in(), GithubSearchCommits.class);
    } catch (IOException e) {
      return false;
    }
    return message.getTotal_count() > 0;
  }

  private String getRealCommit(String repo, String sha) {
    Response response = githubService.getCommit("token " + token, repo, sha);
    ObjectMapper objectMapper = new ObjectMapper();
    GithubCommitMessage message = null;
    try {
      message = objectMapper.readValue(response.getBody().in(), GithubCommitMessage.class);
    } catch (IOException e) {
      return sha;
    }
    return message.getParents().stream()
      .map(GithubCommitParents::getSha)
      .filter(c -> !commitFromMaster(repo, c))
      .findAny()
      .orElse(sha);
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
