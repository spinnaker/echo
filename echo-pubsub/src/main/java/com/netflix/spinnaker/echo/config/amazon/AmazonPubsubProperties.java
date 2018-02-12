/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config.amazon;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "pubsub.amazon")
public class AmazonPubsubProperties {

  @Valid
  private List<AmazonPubsubSubscription> subscriptions;

  @Data
  public static class AmazonPubsubSubscription {

    private static final Logger log = LoggerFactory.getLogger(AmazonPubsubSubscription.class);

    @NotEmpty
    private String name;

    @NotEmpty
    private String accountName;

    @NotEmpty
    private  String topicARN;

    @NotEmpty
    private  String queueARN;

    private String templatePath;

    private MessageFormat messageFormat;

    int visibilityTimeout = 30;
    int sqsMessageRetentionPeriodSeconds = 120;
    int waitTimeSeconds = 5;

    public AmazonPubsubSubscription() {
    }

    public AmazonPubsubSubscription(
      String name,
      String accountName,
      String topicARN,
      String queueARN,
      String templatePath,
      MessageFormat messageFormat) {
      this.name = name;
      this.accountName = accountName;
      this.topicARN = topicARN;
      this.queueARN = queueARN;
      this.templatePath = templatePath;
      this.messageFormat = messageFormat;
    }

    private void determineMessageFormat(){
      if (!StringUtils.isEmpty(templatePath)) {
        messageFormat = MessageFormat.CUSTOM;
      } else if (messageFormat == null || messageFormat.equals("")) {
        messageFormat = MessageFormat.NONE;
      }
      log.info("Message format: {}", messageFormat);
    }

    public InputStream readTemplatePath() {
      determineMessageFormat();
      try {
        if (messageFormat == MessageFormat.CUSTOM) {
            return new FileInputStream(new File(templatePath));
        } else {
          return getClass().getResourceAsStream(messageFormat.jarPath);
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to read template in subscription " + name + ": " + e.getMessage(), e);
      }
    }
  }

  public static enum MessageFormat {
    S3("/amazon/s3.jinja"),
    CUSTOM(),
    NONE();

    private String jarPath = "";

    MessageFormat(String jarPath) {
      this.jarPath = jarPath;
    }

    MessageFormat() { }
  }
}
