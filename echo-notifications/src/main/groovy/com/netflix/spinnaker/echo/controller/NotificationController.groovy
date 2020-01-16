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

package com.netflix.spinnaker.echo.controller

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.notification.InteractiveNotificationCallbackHandler
import com.netflix.spinnaker.echo.notification.NotificationService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/notifications")
@RestController
@Slf4j
class NotificationController {
  @Autowired(required=false)
  Collection<NotificationService> notificationServices

  @Autowired
  InteractiveNotificationCallbackHandler callbackHandler

  @RequestMapping(method = RequestMethod.POST)
  EchoResponse create(@RequestBody Notification notification) {
    notificationServices?.find { it.supportsType(notification.notificationType) }?.handle(notification)
  }

  /**
   * Provides a generic callback API for notification services to call, primarily in response to interactive user
   * action (e.g. clicking a button in a message). This method makes as few assumptions as possible about the request,
   * delegating the raw request headers, parameters and body to the corresponding [InteractiveNotificationService]
   * to process, and similarly allows the notification service to return an arbitrary response body to the caller.
   *
   * @param source The unique ID of the calling notification service (e.g. "slack")
   * @param headers The request headers
   * @param rawBody The raw body of the request
   * @param parameters The request parameters, parsed as a Map
   * @return
   */
  @RequestMapping(
    value = '/callbacks/{source}',
    method = RequestMethod.POST,
    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  ResponseEntity<String> processCallback(
    @PathVariable String source,
    @RequestHeader HttpHeaders headers,
    @RequestBody String rawBody,
    @RequestParam Map parameters) {
    return callbackHandler.processCallback(source, headers, rawBody, parameters)
  }

}
