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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties;
import com.netflix.spinnaker.echo.discovery.DiscoveryActivated;
import com.netflix.spinnaker.echo.pubsub.PubsubEventCreator;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.model.EventCreator;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.kork.aws.ARN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/***
 * Starts the individual SQS workers (one for each subscription)
 */
@Component
@ConditionalOnExpression("${pubsub.enabled:false} && ${pubsub.amazon.enabled:false}")
public class SQSSubscriberProvider implements DiscoveryActivated {
  private static final Logger log = LoggerFactory.getLogger(SQSSubscriberProvider.class);

  private final ObjectMapper objectMapper;
  private final AWSCredentialsProvider awsCredentialsProvider;
  private final AmazonPubsubProperties properties;
  private final PubsubSubscribers pubsubSubscribers;
  private final PubsubMessageHandler.Factory pubsubMessageHandlerFactory;
  private final Registry registry;
  private final MessageArtifactTranslator.Factory messageArtifactTranslatorFactory;

  @Autowired
  SQSSubscriberProvider(ObjectMapper objectMapper,
                        AWSCredentialsProvider awsCredentialsProvider,
                        AmazonPubsubProperties properties,
                        PubsubSubscribers pubsubSubscribers,
                        PubsubMessageHandler.Factory pubsubMessageHandlerFactory,
                        Registry registry,
                        MessageArtifactTranslator.Factory messageArtifactTranslatorFactory) {
    this.objectMapper = objectMapper;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.properties = properties;
    this.pubsubSubscribers = pubsubSubscribers;
    this.pubsubMessageHandlerFactory = pubsubMessageHandlerFactory;
    this.registry = registry;
    this.messageArtifactTranslatorFactory = messageArtifactTranslatorFactory;
  }

  @PostConstruct
  public void start() {
    if (properties == null) {
      return;
    }

    ExecutorService executorService = Executors.newFixedThreadPool(properties.getSubscriptions().size());

    List<PubsubSubscriber> subscribers = new ArrayList<>();

    properties.getSubscriptions().forEach((AmazonPubsubProperties.AmazonPubsubSubscription subscription) -> {
      log.info("Bootstrapping SQS for SNS topic: {}", subscription.getTopicARN());
      if (subscription.getTemplatePath() != null && !subscription.getTemplatePath().equals("")){
        log.info("Using template: {} for subscription: {}",
          subscription.getTemplatePath(),
          subscription.getName());
      }

      ARN queueArn = new ARN(subscription.getQueueARN());

      Optional<MessageArtifactTranslator> messageArtifactTranslator = Optional.empty();
      if (subscription.getMessageFormat() != AmazonPubsubProperties.MessageFormat.NONE) {
        messageArtifactTranslator = Optional.ofNullable(subscription.readTemplatePath())
          .map(messageArtifactTranslatorFactory::createJinja);
      }
      EventCreator eventCreator = new PubsubEventCreator(messageArtifactTranslator);

      SQSSubscriber worker = new SQSSubscriber(
        objectMapper,
        subscription,
        pubsubMessageHandlerFactory.create(eventCreator),
        AmazonSNSClientBuilder
          .standard()
          .withCredentials(awsCredentialsProvider)
          .withClientConfiguration(new ClientConfiguration())
          .withRegion(queueArn.getRegion())
          .build(),
        AmazonSQSClientBuilder
          .standard()
          .withCredentials(awsCredentialsProvider)
          .withClientConfiguration(new ClientConfiguration())
          .withRegion(queueArn.getRegion())
          .build(),
        enabled::get,
        registry
      );

      try {
        executorService.submit(worker);
        subscribers.add(worker);
        log.debug("Created worker for subscription: {}", subscription.getName());
      } catch (RejectedExecutionException e) {
        log.error("Could not start " + worker.getWorkerName(), e);
      }
    });
    pubsubSubscribers.putAll(subscribers);
  }
}
