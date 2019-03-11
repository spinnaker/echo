/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.echo.scm.bitbucket;

import com.netflix.spinnaker.echo.model.Event;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class BitbucketServerPrMergedEvent implements BitbucketWebhookEvent {
  PullRequest pullRequest;

  @Override
  public String getFullRepoName(Event event) {
    return this.pullRequest.toRef.repository.getName();
  }

  @Override
  public String getHash(Event event, Map postedEvent) {
    return Optional.of(this)
      .map(BitbucketServerPrMergedEvent::getPullRequest)
      .map(PullRequest::getProperties)
      .map(Properties::getMergeCommit)
      .map(MergeCommit::getId)
      .orElse("");
  }

  @Override
  public String getBranch(Event event, Map postedEvent) {
    String branch = Optional.of(this)
      .map(BitbucketServerPrMergedEvent::getPullRequest)
      .map(PullRequest::getToRef)
      .map(Ref::getId)
      .orElse("");

    return branch.replace("refs/heads/", "");
  }

  @Override
  public String getRepoProject(Event event, Map postedEvent) {
    return Optional.of(this)
      .map(BitbucketServerPrMergedEvent::getPullRequest)
      .map(PullRequest::getToRef)
      .map(Ref::getRepository)
      .map(Repository::getProject)
      .map(Project::getKey)
      .orElse("");
  }

  @Override
  public String getSlug(Event event, Map postedEvent) {
    return Optional.of(this)
      .map(BitbucketServerPrMergedEvent::getPullRequest)
      .map(PullRequest::getToRef)
      .map(Ref::getRepository)
      .map(Repository::getSlug)
      .orElse("");
  }

  @Data
  private static class Properties {
    MergeCommit mergeCommit;
  }

  @Data
  private static class MergeCommit {
    String id;
  }


  @Data
  private static class PullRequest {
    Ref toRef;
    Properties properties;
  }

  @Data
  private static class Ref {
    String id;
    Repository repository;

  }

  @Data
  private static class Project {
    String key;
  }

  @Data
  private static class Repository {
    String name;
    String slug;
    Project project;
  }
}
