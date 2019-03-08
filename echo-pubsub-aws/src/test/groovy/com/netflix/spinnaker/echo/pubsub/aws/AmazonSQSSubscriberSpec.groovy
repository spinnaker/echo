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

package com.netflix.spinnaker.echo.pubsub.aws

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator
import com.netflix.spinnaker.echo.config.AmazonPubsubProperties
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler
import com.netflix.spinnaker.kork.aws.ARN
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonSQSSubscriberSpec extends Specification {

  AmazonSNS amazonSNS = Mock()
  AmazonSQS amazonSQS = Mock()
  PubsubMessageHandler pubsubMessageHandler = Mock()
  Registry registry = Mock()

  ARN queueARN = new ARN("arn:aws:sqs:us-west-2:100:queueName")
  ARN topicARN = new ARN("arn:aws:sns:us-west-2:100:topicName")
  AmazonPubsubProperties.AmazonPubsubSubscription subscription =
    new AmazonPubsubProperties.AmazonPubsubSubscription('aws_events', topicARN.arn, queueARN.arn, "", null, null, 3600)

  @Shared
  def objectMapper = new ObjectMapper()

  @Subject
  def subject = new SQSSubscriber(
    objectMapper,
    subscription,
    pubsubMessageHandler,
    amazonSNS,
    amazonSQS,
    {true},
    registry,
    new MessageArtifactTranslator.Factory(
      Mock(ApplicationEventPublisher),
      new JinjaArtifactExtractor.Factory(new DefaultJinjavaFactory())
    )
  )

  def 'should unmarshal an sns notification message'() {
    given:
    String payload = '''
      {\"Records\":[
      \"eventVersion\":\"2.0\",
      \"eventSource\":\"aws:s3\",
      \"awsRegion\":\"us-west-2\","
      \"eventName\":\"ObjectCreated:Put\","
      \"s3\":{"
      \"s3SchemaVersion\":\"1.0\","
      \"configurationId\":\"prestaging_front50_events\","
      \"bucket\":{\"name\":\"us-west-2.spinnaker-prod\",\"ownerIdentity\":{\"principalId\":\"A2TW6LBRCW9VEM\"},\"arn\":\"arn:aws:s3:::us-west-2.spinnaker-prod\"},"
      \"object\":{\"key\":\"prestaging/front50/pipelines/31ef9c67-1d67-474f-a653-ac4b94c90817/pipeline-metadata.json\",\"versionId\":\"8eyu4_RfV8EUqTnClhkKfLK5V4El_mIW\"}}"
      "}]}
      '''

    NotificationMessageWrapper notificationMessage = new NotificationMessageWrapper(
      "Notification",
      "4444-ffff",
      "arn:aws:sns:us-west-2:100:topicName",
      "Amazon S3 Notification",
      payload,
      [:]
    )
    String snsMesssage = objectMapper.writeValueAsString(notificationMessage)

    when:
    String result = subject.unmarshalMessageBody(snsMesssage)

    then:
    0 * subject.log.error()
    result == payload
  }

}
