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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.echo.model.Event;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class BitbucketCloudEvent implements BitbucketWebhookEvent {
  Repository repository;
  @JsonProperty("pullrequest")
  PullRequest pullRequest;
  Push push;

  @Override
  public String getFullRepoName(Event event) {
    return this.repository.getFullName();
  }

  @Override
  public String getSlug(Event event, Map postedEvent) {
    return Optional.of(this)
      .map(BitbucketCloudEvent::getRepository)
      .map(Repository::getFullName)
      .orElse("");
  }

  @Override
  public String getRepoProject(Event event, Map postedEvent) {
    return Optional.of(this)
      .map(BitbucketCloudEvent::getRepository)
      .map(Repository::getOwner)
      .map(Owner::getUsername)
      .orElse("");
  }

  @Override
  public String getBranch(Event event, Map postedEvent) {
    if (this.getPullRequest() != null) {
      return Optional.of(this)
        .map(BitbucketCloudEvent::getPullRequest)
        .map(PullRequest::getDestination)
        .map(Destination::getBranch)
        .map(Branch::getName)
        .orElse("");
    }

    return Optional.of(this)
      .map(BitbucketCloudEvent::getPush)
      .map(Push::getFirstChange)
      .map(Change::getNewObj)
      .map(NewObj::getName)
      .orElse("");
  }

  @Override
  public String getHash(Event event, Map postedEvent) {
    if (this.getPullRequest() != null) {
      return Optional.of(this)
        .map(BitbucketCloudEvent::getPullRequest)
        .map(PullRequest::getMergeCommit)
        .map(MergeCommit::getHash)
        .orElse("");
    }

    return Optional.of(this)
      .map(BitbucketCloudEvent::getPush)
      .map(Push::getFirstChange)
      .map(Change::getFirstCommit)
      .map(Commit::getHash)
      .orElse("");
  }

  @Data
  private static class Repository {
    @JsonProperty("full_name")
    String fullName;
    Owner owner;
  }

  @Data
  private static class Owner {
    String username;
  }

  @Data
  private static class PullRequest {
    @JsonProperty("merge_commit")
    MergeCommit mergeCommit;
    Destination destination;
  }

  @Data
  private static class MergeCommit {
    String hash;
  }

  @Data
  private static class Push {
    List<Change> changes;

    public Change getFirstChange() {
      if (this.changes.isEmpty()) {
        return null;
      }
      return this.getChanges().get(0);
    }
  }

  @Data
  private static class Change {
    List<Commit> commits;

    @JsonProperty("new")
    NewObj newObj;

    public Commit getFirstCommit() {
      if (this.commits.isEmpty()) {
        return null;
      }
      return this.getCommits().get(0);
    }
  }

  @Data
  private static class Commit {
    String hash;
  }

  @Data
  private static class NewObj {
    String name;
  }

  @Data
  private static class Destination {
    Branch branch;
  }

  @Data
  private static class Branch {
    String name;
  }
}
