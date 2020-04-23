/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pubsub.google;

import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.netflix.spinnaker.echo.config.GooglePubsubCredentialsProvider;
import com.netflix.spinnaker.echo.config.GooglePubsubProperties.GooglePubsubSubscription;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GooglePubsubSubscriber implements PubsubSubscriber {

  private String subscriptionName;

  private String name;

  private String project;

  private Subscriber subscriber;

  private Credentials credentials;

  private MessageReceiver messageReceiver;

  private static final PubsubSystem pubsubSystem = PubsubSystem.GOOGLE;

  public GooglePubsubSubscriber(
      String name,
      String subscriptionName,
      String project,
      Credentials credentials,
      GooglePubsubMessageReceiver messageReceiver) {
    this.name = name;
    this.subscriptionName = subscriptionName;
    this.project = project;
    this.messageReceiver = messageReceiver;
    this.credentials = credentials;
  }

  @Override
  public PubsubSystem getPubsubSystem() {
    return pubsubSystem;
  }

  @Override
  public String getSubscriptionName() {
    return subscriptionName;
  }

  @Override
  public String getName() {
    return name;
  }

  private static String formatSubscriptionName(String project, String name) {
    return String.format("projects/%s/subscriptions/%s", project, name);
  }

  public static GooglePubsubSubscriber buildSubscriber(
      GooglePubsubSubscription subscription, PubsubMessageHandler pubsubMessageHandler) {
    String subscriptionName = subscription.getSubscriptionName();
    String project = subscription.getProject();
    String jsonPath = subscription.getJsonPath();

    GooglePubsubMessageReceiver messageReceiver =
        new GooglePubsubMessageReceiver(
            subscription.getAckDeadlineSeconds(), subscription.getName(), pubsubMessageHandler);

    Credentials credentials = null;
    try {
      credentials = new GooglePubsubCredentialsProvider(jsonPath).getCredentials();
    } catch (IOException e) {
      log.error("Could not create Google Pubsub json credentials: {}", e.getMessage());
    }

    return new GooglePubsubSubscriber(
        subscription.getName(), subscriptionName, project, credentials, messageReceiver);
  }

  public synchronized void start() {
    this.subscriber =
        Subscriber.newBuilder(
                ProjectSubscriptionName.of(project, subscriptionName), messageReceiver)
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();

    subscriber.addListener(
        new GooglePubsubFailureHandler(this, formatSubscriptionName(project, subscriptionName)),
        MoreExecutors.directExecutor());
    subscriber.startAsync().awaitRunning();
    log.info(
        "Google Pubsub subscriber started for {}",
        formatSubscriptionName(project, subscriptionName));
  }

  public void stop() {
    subscriber.stopAsync().awaitTerminated();
  }

  private void restart() {
    try {
      stop();
    } catch (Exception e) {
      log.warn("Failure stopping subscriber: ", e);
    }

    log.info(
        "Waiting to restart Google Pubsub subscriber for {}",
        formatSubscriptionName(project, subscriptionName));
    try {
      // TODO: Use exponential backoff?
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }

    start();
  }

  private static class GooglePubsubMessageReceiver implements MessageReceiver {

    private int ackDeadlineSeconds;

    private PubsubMessageHandler pubsubMessageHandler;

    /**
     * Logical name given to the subscription by the user, not the locator the pub/sub system uses.
     */
    private String subscriptionName;

    private NodeIdentity identity = new NodeIdentity();

    public GooglePubsubMessageReceiver(
        int ackDeadlineSeconds,
        String subscriptionName,
        PubsubMessageHandler pubsubMessageHandler) {
      this.ackDeadlineSeconds = ackDeadlineSeconds;
      this.subscriptionName = subscriptionName;
      this.pubsubMessageHandler = pubsubMessageHandler;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.getData().toStringUtf8();
      String messageId = message.getMessageId();
      Map<String, String> messageAttributes =
          message.getAttributesMap() == null ? new HashMap<>() : message.getAttributesMap();
      log.debug(
          "Received Google pub/sub message with payload: {}\n and attributes: {}",
          messagePayload,
          messageAttributes);
      MessageDescription description =
          MessageDescription.builder()
              .subscriptionName(subscriptionName)
              .messagePayload(messagePayload)
              .messageAttributes(messageAttributes)
              .pubsubSystem(pubsubSystem)
              .ackDeadlineSeconds(
                  5 * ackDeadlineSeconds) // Set a high upper bound on message processing time.
              .retentionDeadlineSeconds(
                  7 * 24 * 60 * 60) // Expire key after max retention time, which is 7 days.
              .build();
      GoogleMessageAcknowledger acknowledger = new GoogleMessageAcknowledger(consumer);

      pubsubMessageHandler.handleMessage(
          description, acknowledger, identity.getIdentity(), messageId);
    }
  }

  @AllArgsConstructor
  private static class GooglePubsubFailureHandler extends ApiService.Listener {

    private GooglePubsubSubscriber subscriber;
    private String subscriptionName;

    @Override
    public void failed(ApiService.State from, Throwable failure) {
      if (failure.getMessage() != null && failure.getMessage().contains("NOT_FOUND")) {
        log.error(
            "Subscription name {} could not be found (will not retry): ",
            subscriptionName,
            failure);
        return;
      }

      log.error(
          "Google Pubsub listener for subscription name {} failure caused by: ",
          subscriptionName,
          failure);
      subscriber.restart();
    }
  }
}
