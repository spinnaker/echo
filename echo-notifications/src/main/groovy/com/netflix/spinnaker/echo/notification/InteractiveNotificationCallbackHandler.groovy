/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.notification

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.InteractiveActionCallback
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.http.Body
import retrofit.http.Header
import retrofit.http.POST

import static retrofit.Endpoints.newFixedEndpoint

@Slf4j
@Component
class InteractiveNotificationCallbackHandler {
  @Autowired
  Ok3Client spinnakerServiceClient

  @Autowired
  List<InteractiveNotificationService> notificationServices

  @Autowired
  private Environment environment

  /**
   * Processes a callback request from Slack by relaying it to the downstream service which originated the message
   * referenced in the payload.
   * @param event
   */
  Map processCallback(String source, Map content, HttpHeaders headers) {
    log.debug("Received interactive notification callback request from ${source}")

    InteractiveNotificationService notificationService = notificationServices.stream()
      .find { it.supportsType(Notification.Type.valueOf(source.toUpperCase())) }

    if (notificationService == null) {
      throw new IllegalArgumentException("NotificationService for ${source} no registered")
    }

    InteractiveActionCallback callback = notificationService.parseInteractionCallback(content)
    String requestUrl = environment.getProperty("${callback.serviceId}.baseUrl") + "/notifications/callback"
    SpinnakerService spinnakerService = new RestAdapter.Builder()
      .setEndpoint(newFixedEndpoint(requestUrl))
      .setClient(spinnakerServiceClient)
      .setLogLevel(RestAdapter.LogLevel.BASIC)
      .setLog(new Slf4jRetrofitLogger(SpinnakerService.class))
      .setConverter(new JacksonConverter())
      .build()
      .create(SpinnakerService.class)

    log.debug("Routing notification callback to originating service at ${requestUrl}")

    // TODO: error handling (retries?)
    //    retrySupport.retry(
    //      () ->
    Response response = spinnakerService.post(callback, callback.user)
    log.debug("Received callback response from downstream Spinnaker service: ${response}")
    //      5,
    //      2000,
    //      false);
    //  }

    // Allows the notification service implementation to respond to the callback as needed
    notificationService.respondToCallback(content)

    return null
  }

  interface SpinnakerService {
    @POST("/")
    Response post(@Body InteractiveActionCallback callback, @Header('X-SPINNAKER-USER') String user);
  }
}
