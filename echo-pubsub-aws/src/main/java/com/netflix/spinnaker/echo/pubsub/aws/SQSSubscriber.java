/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pubsub.aws;

import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.SetSubscriptionAttributesResult;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * One subscriber for each subscription.
 * The subscriber makes sure the SQS queue is created, subscribes to the SNS topic,
 * polls the queue for messages, and removes them once processed.
 */
public class SQSSubscriber implements Runnable, PubsubSubscriber {

  private static final Logger log = LoggerFactory.getLogger(SQSSubscriber.class);

  private static final int AWS_MAX_NUMBER_OF_MESSAGES = 10;
  static private final PubsubSystem pubsubSystem = PubsubSystem.AMAZON;

  private final ObjectMapper objectMapper;
  private final AmazonSNS amazonSNS;
  private final AmazonSQS amazonSQS;

  private final AmazonPubsubProperties.AmazonPubsubSubscription subscription;

  private final PubsubMessageHandler pubsubMessageHandler;
  private final MessageArtifactTranslator messageArtifactTranslator;

  private final NodeIdentity identity = new NodeIdentity();

  Registry registry;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;

  private final Supplier<Boolean> isEnabled;

  public SQSSubscriber(ObjectMapper objectMapper,
                       AmazonPubsubProperties.AmazonPubsubSubscription subscription,
                       PubsubMessageHandler pubsubMessageHandler,
                       AmazonSNS amazonSNS,
                       AmazonSQS amazonSQS,
                       Supplier<Boolean> isEnabled,
                       Registry registry) {
    this.objectMapper = objectMapper;
    this.subscription = subscription;
    this.pubsubMessageHandler = pubsubMessageHandler;
    this.amazonSNS = amazonSNS;
    this.amazonSQS = amazonSQS;
    this.isEnabled = isEnabled;
    this.registry = registry;

    this.messageArtifactTranslator = new MessageArtifactTranslator(subscription.readTemplatePath());
    this.queueARN = new ARN(subscription.getQueueARN());
    this.topicARN = new ARN(subscription.getTopicARN());
  }

  public String getWorkerName() {
    return queueARN.getArn() + "/" + SQSSubscriber.class.getSimpleName();
  }

  @Override
  public PubsubSystem pubsubSystem() {
    return pubsubSystem;
  }

  @Override
  public String subscriptionName() {
    return subscription.getName();
  }

  @Override
  public String getName() {
    return subscriptionName();
  }

  @Override
  public void run() {
    log.info("Starting " + getWorkerName());
    initializeQueue();

    while (true) {
      try {
        listenForMessages();
      } catch (QueueDoesNotExistException e){
        log.warn("Queue {} does not exist, recreating", queueARN);
        initializeQueue();
      } catch (Exception e) {
        log.error("Unexpected error running " + getWorkerName() + ", restarting worker", e);
        try {
          Thread.sleep(500);
        } catch (InterruptedException e1) {
          log.error("Thread {} interrupted while sleeping", getWorkerName(), e1);
        }
      }
    }
  }

  private void initializeQueue() {
    this.queueId = ensureQueueExists(
      amazonSQS, queueARN, topicARN, subscription.getSqsMessageRetentionPeriodSeconds()
    );
    setUpSubscription(amazonSNS, topicARN, queueARN, subscription);
  }

  private void listenForMessages() {
    while (isEnabled.get()) {
      ReceiveMessageResult receiveMessageResult = amazonSQS.receiveMessage(
        new ReceiveMessageRequest(queueId)
          .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
          .withVisibilityTimeout(subscription.getVisibilityTimeout())
          .withWaitTimeSeconds(subscription.getWaitTimeSeconds())
      );

      if (receiveMessageResult.getMessages().isEmpty()) {
        log.debug("Received no messages for queue: {}", queueARN);
        continue;
      }

      receiveMessageResult.getMessages().forEach(message -> {
        handleMessage(message);
      });
    }
  }

  private void handleMessage(Message message) {
    try {
      String messageId = message.getMessageId();
      String messagePayload = unmarshalMessageBody(message.getBody());

      Map<String, String> stringifiedMessageAttributes = message.getMessageAttributes().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

      log.debug("Received Amazon sqs message: {} with payload: {} and attributes: {}", messageId, messagePayload, stringifiedMessageAttributes);

      MessageDescription description = MessageDescription.builder()
        .subscriptionName(subscriptionName())
        .messagePayload(messagePayload)
        .messageAttributes(stringifiedMessageAttributes)
        .pubsubSystem(pubsubSystem)
        .ackDeadlineMillis(TimeUnit.SECONDS.toMillis(50)) // Set a high upper bound on message processing time.
        .retentionDeadlineMillis(TimeUnit.DAYS.toMillis(7)) // Expire key after max retention time, which is 7 days.
        .build();

      AmazonMessageAcknowledger acknowledger = new AmazonMessageAcknowledger(amazonSQS, queueId, message, registry, getName());

      if (subscription.getMessageFormat() != AmazonPubsubProperties.MessageFormat.NONE) {
        description.setArtifacts(parseArtifacts(description.getMessagePayload(), messageId));
      }
      pubsubMessageHandler.handleMessage(description, acknowledger, identity.getIdentity(), messageId);
    } catch (Exception e) {
      log.error("Message {} from queue {} failed to be handled", message, queueId);
      // Todo emjburns: add dead-letter queue policy
    }
  }

  private List<Artifact> parseArtifacts(String messagePayload, String messageId){
    List<Artifact> artifacts = messageArtifactTranslator.parseArtifacts(messagePayload);
    if (artifacts == null || artifacts.size() == 0) {
      log.debug("No artifacts were found for subscription: {} and messageId: {}", subscription.getName(), messageId);
    } else {
      log.debug(
        "Artifacts found for subscription: {}: {}",
        subscription.getName(),
        String.join(", ", artifacts.stream().map(Artifact::toString).collect(Collectors.toList()))
      );
    }
    return artifacts;
  }

  private String unmarshalMessageBody(String messageBody) {
    String messagePayload = messageBody;
    try {
      NotificationMessageWrapper wrapper = objectMapper.readValue(messagePayload, NotificationMessageWrapper.class);
      if (wrapper != null && wrapper.getMessage() != null) {
        messagePayload = wrapper.getMessage();
      }
    } catch (IOException e) {
      // Try to unwrap a notification message; if that doesn't work,
      // we're dealing with a message we can't parse. The template or
      // the pipeline potentially knows how to deal with it.
      log.error("Unable to unmarshal NotificationMessageWrapper. Unknown message type. (body: {})", messageBody, e);
    }
    return messagePayload;
  }

  // Todo emjburns: pull to kork-aws
  private static String ensureQueueExists(AmazonSQS amazonSQS,
                                          ARN queueARN,
                                          ARN topicARN,
                                          int sqsMessageRetentionPeriodSeconds) {
    String queueUrl = amazonSQS.createQueue(queueARN.getName()).getQueueUrl();
    log.debug("Created queue " + queueUrl);

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("Policy", buildSQSPolicy(queueARN, topicARN).toJson());
    attributes.put("MessageRetentionPeriod", Integer.toString(sqsMessageRetentionPeriodSeconds));
    amazonSQS.setQueueAttributes(
      queueUrl,
      attributes
    );

    return queueUrl;
  }

  private void setUpSubscription(AmazonSNS amazonSNS,
                                 ARN topicARN,
                                 ARN queueARN,
                                 AmazonPubsubProperties.AmazonPubsubSubscription subscription){
    log.debug("Initializing subscription and updating filter policies for subscription {}", subscription.getName());
    String subscriptionARN = subscribeToTopic(amazonSNS, topicARN, queueARN);
    applyFilterPolicyToSubscription(amazonSNS, topicARN, queueARN, subscriptionARN, subscription.getFilterPolicy());
  }

  private static String subscribeToTopic(AmazonSNS amazonSNS,
                                         ARN topicARN,
                                         ARN queueARN) {
      SubscribeResult subscribeResult = amazonSNS.subscribe(topicARN.getArn(), "sqs", queueARN.getArn());
      return subscribeResult.getSubscriptionArn();
  }

  private static void applyFilterPolicyToSubscription(AmazonSNS amazonSNS, ARN topicARN, ARN queueARN, String subscriptionArn, String filterPolicy) {
    if (filterPolicy == null || filterPolicy.equals("")) {
      // Refresh subscription to ensure old filterPolicies aren't taking effect.
      // AWS SNS doesn't have an API for deleting filter policies.
      refreshSubscription(amazonSNS, topicARN, queueARN, subscriptionArn);
    } else {
      try {
        SetSubscriptionAttributesResult result = amazonSNS.setSubscriptionAttributes(subscriptionArn, "FilterPolicy", filterPolicy);
      } catch (Exception e) {
        log.error("Unable to set filter policy for subscription {}", subscriptionArn, e);
      }
    }
  }

  private static String refreshSubscription(AmazonSNS amazonSNS, ARN topicARN, ARN queueARN, String subscriptionArn){
    log.debug("Refreshing subscription for topic {} and queue {}", topicARN, queueARN);
    try {
      amazonSNS.unsubscribe(subscriptionArn);
    } catch (Exception e) {
      log.error("Unable to unsubscribe from queue {} from topic {}.", queueARN, topicARN, e);
    }
    return subscribeToTopic(amazonSNS, topicARN, queueARN);
  }

  // Todo emjburns: pull to kork-aws
  /**
   * This policy allows operators to choose whether or not to have pubsub messages to be sent via SNS for fanout, or
   * be sent directly to an SQS queue from the autoscaling group.
   */
  private static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement snsStatement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    snsStatement.setPrincipals(Principal.All);
    snsStatement.setResources(Collections.singletonList(new Resource(queue.getArn())));
    snsStatement.setConditions(Collections.singletonList(
      new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(topic.getArn())
    ));

    Statement sqsStatement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage, SQSActions.GetQueueUrl);
    sqsStatement.setPrincipals(Principal.All);
    sqsStatement.setResources(Collections.singletonList(new Resource(queue.getArn())));

    return new Policy("allow-sns-or-sqs-send", Arrays.asList(snsStatement, sqsStatement));
  }
}
