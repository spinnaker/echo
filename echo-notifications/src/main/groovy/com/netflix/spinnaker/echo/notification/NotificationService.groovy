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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.InteractiveActionCallback
import com.netflix.spinnaker.echo.controller.EchoResponse
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity

interface NotificationService {
  boolean supportsType(Notification.Type type)
  EchoResponse handle(Notification notification)
}

interface InteractiveNotificationService extends NotificationService {
  /**
   * Translate the contents received by echo on the generic notification callbacks API into a generic callback
   * object that can be forwarded to downstream Spinnaker services for actual processing.
   * @param content
   * @return
   */
  InteractiveActionCallback parseInteractionCallback(RequestEntity<String> request)

  /**
   * Gives an opportunity to the notification service to respond to the original callback in a service-specific
   * manner (e.g. Slack provides a `response_url` in the payload that can be called to interact with the original
   * Slack notification message).
   *
   * @param content
   */
  Optional<ResponseEntity<String>> respondToCallback(RequestEntity<String> request)
}
