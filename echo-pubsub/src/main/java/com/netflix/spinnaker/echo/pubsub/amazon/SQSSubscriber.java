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

package com.netflix.spinnaker.echo.pubsub.amazon;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.amazon.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.exceptions.AmazonMessageParsingException;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
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

  private ObjectMapper objectMapper;
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private AmazonSNS amazonSNS;
  private AmazonSQS amazonSQS;

  private AmazonPubsubProperties.AmazonPubsubSubscription subscription;

  private PubsubMessageHandler pubsubMessageHandler;
  private MessageArtifactTranslator messageArtifactTranslator;

  private NodeIdentity identity = new NodeIdentity();

  Registry registry;

  private final ARN queueARN;
  private final ARN topicARN;

  private String queueId = null;


  public SQSSubscriber(ObjectMapper objectMapper,
                       AmazonClientProvider amazonClientProvider,
                       AWSCredentialsProvider awsCredentialsProvider,
                       AmazonPubsubProperties.AmazonPubsubSubscription subscription,
                       PubsubMessageHandler pubsubMessageHandler,
                       AmazonSNS amazonSNS,
                       AmazonSQS amazonSQS,
                       Registry registry) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.subscription = subscription;
    this.pubsubMessageHandler = pubsubMessageHandler; // needed for the redis queue deduping
    this.amazonSNS = amazonSNS;
    this.amazonSQS = amazonSQS;
    this.registry = registry;

    this.messageArtifactTranslator = new MessageArtifactTranslator(subscription.readTemplatePath());
    this.queueARN = new ARN(subscription.getQueueARN());
    this.topicARN = new ARN(subscription.getTopicARN());
  }

  public String getWorkerName() {
    return queueARN.arn + "/" + SQSSubscriber.class.getSimpleName();
  }

  @Override
  public PubsubSystem pubsubSystem() {
    return pubsubSystem;
  }

  @Override
  public String subscriptionName() { //correct?
    return subscription.getName();
  }

  @Override
  public String getName() { //correct?
    return getWorkerName();
  }

  @Override
  public void run() {
    log.info("Starting " + getWorkerName());

    while (true) {
      try {
        listenForMessages();
      } catch (Throwable e) {
        log.error("Unexpected error running " + getWorkerName() + ", restarting", e);
      }
    }
  }

  private void listenForMessages() {
    this.queueId = ensureQueueExists(
      amazonSQS, queueARN, topicARN, subscription.getSqsMessageRetentionPeriodSeconds()
    );
    subscribeToTopic(amazonSNS, topicARN, queueARN);

    while (true) {
      ReceiveMessageResult receiveMessageResult = amazonSQS.receiveMessage(
        new ReceiveMessageRequest(queueId)
          .withMaxNumberOfMessages(AWS_MAX_NUMBER_OF_MESSAGES)
          .withVisibilityTimeout(subscription.getVisibilityTimeout())
          .withWaitTimeSeconds(subscription.getWaitTimeSeconds())
      );

      if (receiveMessageResult.getMessages().isEmpty()) {
        log.debug("Received no messages");
        continue;
      }

      receiveMessageResult.getMessages().forEach(message -> {
        String messageId = message.getMessageId();
        String msgBody = message.getBody();
        String messagePayload;
        try {
          messagePayload = objectMapper.readValue(msgBody, Map.class).get("Message").toString();
        } catch (IOException e) {
          throw new AmazonMessageParsingException(e);
        }

        Map<String, MessageAttributeValue> messageAttributes = message.getMessageAttributes();
        Map<String, String> stringifiedMessageAttributes = messageAttributes.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        log.debug("Received Amazon sqs message with payload: {}\n and attributes: {}", messagePayload, messageAttributes);
        MessageDescription description = MessageDescription.builder()
          .subscriptionName(subscriptionName())
          .messagePayload(messagePayload)
          .messageAttributes(stringifiedMessageAttributes)
          .pubsubSystem(pubsubSystem)
          .ackDeadlineMillis(5 * TimeUnit.SECONDS.toMillis(10)) // Set a high upper bound on message processing time.
          .retentionDeadlineMillis(TimeUnit.DAYS.toMillis(7)) // Expire key after max retention time, which is 7 days.
          .build();

        AmazonMessageAcknowledger acknowledger = new AmazonMessageAcknowledger(amazonSQS, queueId, message);

        if (subscription.getMessageFormat() != AmazonPubsubProperties.MessageFormat.NONE) {
          try {
            List<Artifact> artifacts = messageArtifactTranslator.parseArtifacts(messagePayload);
            artifacts.forEach( a -> a.setArtifactAccount(subscription.getAccountName()));
            description.setArtifacts(artifacts);
            log.info("artifacts {}", String.join(", ", artifacts.stream().map(Artifact::toString).collect(Collectors.toList())));
          } catch (Exception e) {
            log.error("Failed to process artifacts: {}", e.getMessage(), e);
            pubsubMessageHandler.handleFailedMessage(description, acknowledger, identity.getIdentity(), messageId);
          }
        }

        pubsubMessageHandler.handleMessage(description, acknowledger, identity.getIdentity(), messageId);
        //TODO: metrics ya ya
//        registry.counter(getProcessedMetricId(queueARN.region)).increment();
      });
    }
  }

  private static String  ensureQueueExists(AmazonSQS amazonSQS,
                                          ARN queueARN,
                                          ARN topicARN,
                                          int sqsMessageRetentionPeriodSeconds) {
    String queueUrl = amazonSQS.createQueue(queueARN.name).getQueueUrl();
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

  private static String subscribeToTopic(AmazonSNS amazonSNS,
                                         ARN topicARN,
                                         ARN queueARN) {
    amazonSNS.subscribe(topicARN.arn, "sqs", queueARN.arn);
    return topicARN.arn;
  }

  /**
   * This policy allows operators to choose whether or not to have lifecycle hooks to be sent via SNS for fanout, or
   * be sent directly to an SQS queue from the autoscaling group.
   */
  private static Policy buildSQSPolicy(ARN queue, ARN topic) {
    Statement snsStatement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage);
    snsStatement.setPrincipals(Principal.All);
    snsStatement.setResources(Collections.singletonList(new Resource(queue.arn)));
    snsStatement.setConditions(Collections.singletonList(
      new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(topic.arn)
    ));

    Statement sqsStatement = new Statement(Statement.Effect.Allow).withActions(SQSActions.SendMessage, SQSActions.GetQueueUrl);
    sqsStatement.setPrincipals(Principal.All);
    sqsStatement.setResources(Collections.singletonList(new Resource(queue.arn)));

    return new Policy("allow-sns-or-sqs-send", Arrays.asList(snsStatement, sqsStatement));
  }
}
