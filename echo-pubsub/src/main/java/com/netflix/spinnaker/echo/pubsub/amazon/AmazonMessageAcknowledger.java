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

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.netflix.spinnaker.echo.pubsub.model.MessageAcknowledger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responds to the SQS queue for each message using the unique messageReceiptHandle
 * of the message.
 */
public class AmazonMessageAcknowledger implements MessageAcknowledger {

  private static final Logger log = LoggerFactory.getLogger(AmazonMessageAcknowledger.class);

  private AmazonSQS amazonSQS;
  private String queueUrl;
  private Message message;

  @Override
  public void ack() {
    // Delete from queue
    try {
      amazonSQS.deleteMessage(queueUrl, message.getReceiptHandle());
      log.debug("Deleted message: {} from queue {}", message.getMessageId(), queueUrl);
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn(
        "Error deleting message: {}, reason: {} (receiptHandle: {})",
        message.getMessageId(),
        e.getMessage(),
        message.getReceiptHandle()
      );
    }
  }

  @Override
  public void nack() {
    // Set visibility timeout to 0, so that the message can be processed by another worker
    try {
      amazonSQS.changeMessageVisibility(queueUrl, message.getReceiptHandle(), 0);
      log.debug("Changed visibility timeout of message: {} from queue: {}", message.getMessageId(), queueUrl);
    } catch (ReceiptHandleIsInvalidException e) {
      log.warn("Error nack-ing message: {}, reason: {} (receiptHandle: {})",
        message.getMessageId(),
        e.getMessage(),
        message.getReceiptHandle()
      );
    }
  }

  public AmazonMessageAcknowledger(AmazonSQS amazonSQS, String queueUrl, Message message) {
    this.amazonSQS = amazonSQS;
    this.queueUrl = queueUrl;
    this.message = message;
  }
}
