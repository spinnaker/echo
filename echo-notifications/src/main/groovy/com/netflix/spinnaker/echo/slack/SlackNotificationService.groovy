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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.InteractiveActionCallback
import com.netflix.spinnaker.echo.controller.EchoResponse
import com.netflix.spinnaker.echo.notification.InteractiveNotificationService
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.http.Body
import retrofit.http.POST
import retrofit.http.Path
import static retrofit.Endpoints.newFixedEndpoint

@Slf4j
@Component
@ConditionalOnProperty('slack.enabled')
class SlackNotificationService implements InteractiveNotificationService {
  private static Notification.Type TYPE = Notification.Type.SLACK

  private SlackService slack
  private Client retrofitClient
  private NotificationTemplateEngine notificationTemplateEngine
  private ObjectMapper objectMapper

  SlackNotificationService(
    SlackService slack,
    Client retrofitClient,
    NotificationTemplateEngine notificationTemplateEngine,
    ObjectMapper objectMapper
  ) {
    this.slack = slack
    this.retrofitClient = retrofitClient
    this.notificationTemplateEngine = notificationTemplateEngine
    this.objectMapper = objectMapper
  }

  @Override
  boolean supportsType(Notification.Type type) {
    return type == TYPE
  }

  @Override
  EchoResponse.Void handle(Notification notification) {
    def text = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY)
    notification.to.each {
      def response
      String address = it.startsWith('#') ? it : "#${it}"
      if (slack.configProperties.sendCompactMessages) {
        response = slack.sendCompactMessage(new CompactSlackMessage(text), address, true)
      } else {
        response = slack.sendMessage(
          new SlackAttachment("Spinnaker Notification", text, notification.interactiveActions),
          address, true)
      }
      log.trace("Received response from Slack: {} {} for message '{}'. {}",
        response?.status, response?.reason, text, response?.body)
    }

    new EchoResponse.Void()
  }

  private Map parseSlackPayload(String body) {
    if (!body.startsWith("payload=")) {
      throw new InvalidRequestException("Missing payload field in Slack callback request.")
    }

    Map payload = objectMapper.readValue(
      // Slack requests use application/x-www-form-urlencoded
      URLDecoder.decode(body.split("payload=")[1], "UTF-8"),
      Map)

    // currently supporting only interactive actions
    if (payload.type != "interactive_message") {
      throw new InvalidRequestException("Unsupported Slack callback type: ${payload.type}")
    }

    if (!payload.callback_id || !payload.user?.name) {
      throw new InvalidRequestException("Slack callback_id and user not present. Cannot route the request to originating Spinnaker service.")
    }

    payload
  }

  @Override
  InteractiveActionCallback parseInteractionCallback(HttpHeaders headers, String body, Map parameters) {
    // TODO(lfp): This currently doesn't work -- troubleshooting with Slack support.
    // slack.verifySignature(headers, body)

    Map payload = parseSlackPayload(body)
    log.debug("Received callback event from Slack of type ${payload.type}")
    slack.verifyToken(payload.token)

    if (payload.actions.size > 1) {
      log.warn("Expected a single selected action from Slack, but received ${payload.actions.size}")
    }

    if (payload.actions[0].type != "button") {
      throw new InvalidRequestException("Spinnaker currently only supports Slack button actions.")
    }

    def (serviceId, callbackId) = payload.callback_id.split(":")

    String user = payload.user.name
    try {
      SlackService.SlackUserInfo userInfo = slack.getUserInfo(payload.user.id)
      user = userInfo.email
    } catch (Exception e) {
      log.error("Error retrieving info for Slack user ${payload.user.name} (${payload.user.id}). Falling back to username.")
    }

    new InteractiveActionCallback(
      serviceId: serviceId,
      messageId: callbackId,
      user: user,
      actionPerformed: new Notification.ButtonAction(
        name: payload.actions[0].name,
        label: payload.actions[0].text,
        value: payload.actions[0].value
      )
    )
  }

  @Override
  Optional<ResponseEntity<String>> respondToCallback(String body) {
    Map payload = parseSlackPayload(body)
    log.debug("Responding to Slack callback via ${payload.response_url}")

    // Example: https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX
    URI responseUrl = new URI(payload.response_url)

    SlackHookService slackHookService = new RestAdapter.Builder()
      .setEndpoint(newFixedEndpoint("${responseUrl.scheme}://${responseUrl.host}"))
      .setClient(retrofitClient)
      .setLogLevel(RestAdapter.LogLevel.FULL)
      .setLog(new Slf4jRetrofitLogger(SlackHookService.class))
      .setConverter(new JacksonConverter())
      .build()
      .create(SlackHookService.class)

    def selectedAction = payload.actions[0]
    def attachment = payload.original_message.attachments[0] // we support a single attachment as per Echo notifications
    def selectedActionText = attachment.actions.stream().find {
      it.type == selectedAction.type && it.value == selectedAction.value
    }.text

    Map message = [:]
    message.putAll(payload.original_message)
    message.attachments[0].remove("actions")
    message.attachments[0].text += "\n\nUser <@${payload.user.id}> clicked the *${selectedActionText}* action."

    Response response = slackHookService.respondToMessage(responseUrl.path, message)
    log.debug("Response from Slack: ${response.toString()}")

    return Optional.empty()
  }

  interface SlackHookService {
    @POST('/{path}')
    Response respondToMessage(@Path(value = "path", encode = false) path, @Body Map content)
  }
}
