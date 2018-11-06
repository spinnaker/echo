/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Wither;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the values we include in toString are meaningful, they are hashed and become part of generateFallbackId()
 */
@JsonDeserialize(builder = Trigger.TriggerBuilder.class)
@Builder(toBuilder = true)
@Wither
@ToString(of = {"id", "parent", "type", "master", "job", "cronExpression", "source", "project", "slug", "account", "repository", "tag", "parameters", "payloadConstraints", "attributeConstraints", "branch", "runAsUser", "subscriptionName", "pubsubSystem", "expectedArtifactIds", "payload", "status", "artifactName", "link", "linkText"}, includeFieldNames = false)
@Value
public class Trigger {
  public enum Type {
    CRON("cron"),
    GIT("git"),
    JENKINS("jenkins"),
    DOCKER("docker"),
    WEBHOOK("webhook"),
    PUBSUB("pubsub"),
    DRYRUN("dryrun");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    @Override
    public String toString() {
      return type;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(Trigger.class);

  String id;
  String type;
  boolean enabled;

  // Configuration for git triggers
  String project;
  String slug;
  String source;
  String branch;

  // Configuration for Jenkins triggers
  String master;
  String job;
  String propertyFile;

  // Configuration for cron triggers
  String cronExpression;

  // Configuration for pubsub triggers
  /**
   * Logical name given to the subscription by the user, not the locator
   * the pub/sub system uses.
   */
  String subscriptionName;
  String pubsubSystem;

  // Configuration for docker triggers
  String account;
  String repository;
  String tag;

  // Constraints for webhook and pubsub
  Map payloadConstraints;
  Map attributeConstraints;

  // Configuration for pipeline triggers
  List<String> status;
  String user;

  // Artifact constraints
  List<String> expectedArtifactIds;

  /**
   * Field to use for custom triggers involving artifacts
   */
  String artifactName;

  /**
   * Properties that are bound at run-time
   */
  Integer buildNumber;
  String hash;
  Map<String, Object> buildInfo;
  Map<String, Object> properties;
  Map parameters;
  Map payload;
  String runAsUser;
  String secret;
  String digest;

  @Builder.Default
  boolean rebake = false;

  @Builder.Default
  boolean dryRun = false;

  List<Map<String, Object>> notifications;

  /**
   * Unique ID of a trigger that can be used to correlate a pipeline execution with its trigger.
   */
  String eventId;

  Map<String, ?> lastSuccessfulExecution;

  // url to triggering event
  String link;

  // text to display when linking to triggering event
  String linkText;

  // this is set after deserialization, not in the json representation
  @JsonIgnore
  Pipeline parent;

  @JsonIgnore
  boolean propagateAuth;

  public String generateFallbackId() {
    return UUID.nameUUIDFromBytes(this.toString().getBytes()).toString();
  }

  public Trigger atBuildNumber(final int buildNumber) {
    return this.toBuilder()
        .buildNumber(buildNumber)
        .hash(null)
        .tag(null)
        .build();
  }

  public Trigger atHash(final String hash) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(hash)
        .tag(null)
        .build();
  }

  public Trigger atBranch(final String branch) {
    return this.toBuilder()
        .buildNumber(null)
        .tag(null)
        .branch(branch)
        .build();
  }

  public Trigger atTag(final String tag) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(null)
        .tag(tag)
        .build();
  }

  public Trigger atPayload(final Map payload) {
    return this.toBuilder()
      .payload(payload)
      .build();
  }

  public Trigger atParameters(final Map parameters) {
    return this.toBuilder()
        .parameters(parameters)
        .build();
  }

  public Trigger atSecret(final String secret) {
    return this.toBuilder()
        .buildNumber(null)
        .hash(null)
        .digest(null)
        .secret(secret)
        .build();
  }

  public Trigger atMessageDescription(final String subscriptionName, final String pubsubSystem) {
    return this.toBuilder()
        .subscriptionName(subscriptionName)
        .pubsubSystem(pubsubSystem)
        .build();
  }

  public Trigger atEventId(final String eventId) {
    return this.toBuilder()
      .eventId(eventId)
      .build();
  }

  public Trigger atNotifications(final List<Map<String,Object>> notifications) {
    return this.toBuilder()
      .notifications(notifications)
      .build();
  }

  public Trigger atPropagateAuth(final boolean propagateAuth) {
    return this.toBuilder()
      .propagateAuth(propagateAuth)
      .build();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class TriggerBuilder {
    // When deserializing triggers, always ignore the value of propagateAuth, which should only
    // be set by Echo.
    @JsonIgnore
    private TriggerBuilder propagateAuth(boolean propagateAuth) {
      return this;
    }
  }
}
