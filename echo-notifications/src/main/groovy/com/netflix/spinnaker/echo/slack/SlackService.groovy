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


package com.netflix.spinnaker.echo.slack

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.echo.config.SlackConfig
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import groovy.json.JsonBuilder
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.codec.Hex
import retrofit.client.Response

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.InvalidKeyException
import java.time.Duration
import java.time.Instant

@Canonical
@Slf4j
class SlackService {
  SlackClient slackClient
  SlackConfig.SlackProperties configProperties

  Response sendCompactMessage(CompactSlackMessage message, String channel, boolean asUser) {
    slackClient.sendMessage(configProperties.token, message.buildMessage(), channel, asUser)
  }

  Response sendMessage(SlackAttachment message, String channel, boolean asUser) {
    configProperties.useIncomingWebhook() ?
      slackClient.sendUsingIncomingWebHook(configProperties.token, new SlackRequest([message], channel)) :
      slackClient.sendMessage(configProperties.token, toJson(message), channel, asUser)
  }

  SlackUserInfo getUserInfo(String userId) {
    slackClient.getUserInfo(configProperties.token, userId)
  }

  // Reference: https://api.slack.com/docs/verifying-requests-from-slack
  // FIXME (lfp): this algorithm works as I've validated it against the sample data provided in the Slack documentation,
  //  but it doesn't work with our requests and signing secret for some reason. I've reached out to Slack support but
  //  have not received any definitive answers yet.
  void verifySignature(HttpHeaders headers, String body) {
    String timestamp = headers['X-Slack-Request-Timestamp'].first()
    String signature = headers['X-Slack-Signature'].first()

    if ((Instant.ofEpochSecond(Long.valueOf(timestamp)) + Duration.ofMinutes(5)).isBefore(Instant.now())) {
      // The request timestamp is more than five minutes from local time. It could be a replay attack.
      throw new InvalidRequestException("Slack request timestamp is older than 5 minutes. Replay attack?")
    }

    String signatureBaseString = 'v0:' + timestamp + ':' + body

    try {
      Mac mac = Mac.getInstance("HmacSHA256")
      SecretKeySpec secretKeySpec = new SecretKeySpec(configProperties.signingSecret.getBytes(), "HmacSHA256")
      mac.init(secretKeySpec)
      byte[] digest = mac.doFinal(signatureBaseString.getBytes())
      String calculatedSignature = "v0=" + Hex.encode(digest).toString()

      if (calculatedSignature != signature) {
        throw new InvalidRequestException("Invalid Slack signature header.")
      }
    } catch (InvalidKeyException e) {
      throw new InvalidRequestException("Invalid key exception verifying Slack request signature.")
    }
  }

  void verifyToken(String receivedToken) {
    if (receivedToken != configProperties.verificationToken) {
      throw new InvalidRequestException("Token received from Slack does not match verification token.")
    }
  }

  def static toJson(message) {
    "[" + new JsonBuilder(message).toPrettyString() + "]"
  }

  // Partial view into the response from Slack, but enough for our needs
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class SlackUserInfo {
    String id
    String name
    String realName
    String email
    boolean deleted
    boolean has2fa

    @JsonProperty('user')
    private void unpack(Map user) {
      this.id = user.id
      this.name = user.name
      this.realName = user.real_name
      this.deleted = user.deleted
      this.has2fa = user.has_2fa
      this.email = user.profile.email
    }
  }
}
