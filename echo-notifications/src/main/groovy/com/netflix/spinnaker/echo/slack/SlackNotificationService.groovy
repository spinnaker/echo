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
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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

  @Value('${slack.token:}')
  String token

  @Value('${slack.send-compact-messages:false}')
  Boolean sendCompactMessages

  SlackService slack
  Client retrofitClient
  NotificationTemplateEngine notificationTemplateEngine
  ObjectMapper objectMapper

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
      if (sendCompactMessages) {
        response = slack.sendCompactMessage(token, new CompactSlackMessage(text), address, true)
      } else {
        response = slack.sendMessage(token,
          new SlackAttachment("Spinnaker Notification", text, notification.interactiveActions),
          address, true)
      }
      log.trace("Received response from Slack: {} {} for message '{}'. {}",
        response?.status, response?.reason, text, response?.body)
    }

    new EchoResponse.Void()
  }

  private Map parseSlackPayload(Map content) {
    if (!content.payload) {
      throw new IllegalArgumentException("Missing payload field in Slack callback request.")
    }

    Map payload = objectMapper.readValue(content.payload, Map)

    // currently supporting only interactive actions
    if (payload.type != "interactive_message") {
      throw new IllegalArgumentException("Unsupported Slack callback type: ${payload.type}")
    }

    if (!payload.callback_id || !payload.user?.name) {
      throw new IllegalArgumentException("Slack callback_id and user not present. Cannot route the request to originating Spinnaker service.")
    }

    payload
  }

  @Override
  InteractiveActionCallback parseInteractionCallback(Map content) {
    log.debug("Received callback event from Slack of type ${content.type}")
    // TODO: validate the X-Slack-Signature header (https://api.slack.com/docs/verifying-requests-from-slack)

    Map payload = parseSlackPayload(content)

    def (serviceId, callbackId) = payload.callback_id.split(":")
    String user = payload.user.name
    // TODO: get user e-mail from Slack API so it matches what we use in X-SPINNAKER-USER
    new InteractiveActionCallback(
      serviceId: serviceId,
      messageId: callbackId,
      user: user,
      // TODO: buttons only for now -- check
      actionPerformed: new Notification.ButtonAction(
        name: payload.actions[0].name,
        label: payload.actions[0].text,
        value: payload.actions[0].value
      )
    )
  }

  @Override
  void respondToCallback(Map content) {
    Map payload = parseSlackPayload(content)
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
    def attachment = payload.original_message.attachments[0] // se support a single attachment as per Echo notifications
    def selectedActionText = attachment.actions.stream().find {
      it.type == selectedAction.type && it.value == selectedAction.value
    }.text

    //String message = attachment.text + "\n\nUser ${payload.user.name} clicked the *${selectedActionText}* action."
    Map message = [:]
    message.putAll(payload.original_message)
    message.attachments[0].remove("actions")
    message.attachments[0].text += "\n\nUser <@${payload.user.id}> clicked the *${selectedActionText}* action."

    Response response = slackHookService.respondToMessage(responseUrl.path, message)
    log.debug("Response from Slack: ${response.toString()}")
  }

  interface SlackHookService {
    @POST('/{path}')
    Response respondToMessage(@Path(value = "path", encode = false) path, @Body Map content)
  }

}
