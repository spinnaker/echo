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

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.controller.NotificationController
import com.netflix.spinnaker.echo.notification.InteractiveNotificationCallbackHandler.SpinnakerService
import spock.lang.Specification
import spock.lang.Subject

class NotificationControllerSpec extends Specification {
  SpinnakerService spinnakerService
  NotificationService notificationService

  @Subject
  NotificationController notificationController

  void setup() {
    notificationService = Mock()
    spinnakerService = Mock()
    notificationController = new NotificationController(
      notificationServices: [ notificationService ]
    )
  }

  void 'creating a notification delegates to the appropriate service'() {
    given:
    Notification notification = new Notification()
    notification.notificationType = Notification.Type.SLACK

    notificationService.supportsType(Notification.Type.SLACK) >> true

    when:
    notificationController.create(notification)

    then:
    1 * notificationService.handle(notification)
  }
}
