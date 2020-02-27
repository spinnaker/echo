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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.events.EventListener
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.services.Front50Service
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@Slf4j
abstract class AbstractEventNotificationAgent implements EventListener {

  @Autowired
  Front50Service front50Service

  @Autowired(required = false)
  protected ObjectMapper mapper

  @Value('${spinnaker.base-url}')
  String spinnakerUrl

  static Map CONFIG = [
    'orchestration': [
      type: 'orchestration',
      link: 'tasks'
    ],
    'pipeline': [
      type: 'pipeline',
      link: 'executions/details'
    ],
    'task'    : [
      type: 'task',
      link: 'tasks'
    ],
    'stage'   : [
      type: 'stage',
      link: 'stage'
    ]
  ]

  @Override
  void processEvent(Event event) {
    if (log.isDebugEnabled() && mapper != null && !event.getDetails().getType().equals("pubsub")) {
      log.debug("Event received: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(event))
    }

    if (!event.details.type.startsWith("orca:")) {
      return
    }

    List eventDetails = event.details.type.split(':')

    Map<String, String> config = CONFIG[eventDetails[1]]
    String status = eventDetails[2]

    if (!config || !config.type) {
      return
    }

    if (config.type == 'task' && event.content.standalone == false) {
      return
    }

    if (config.type == 'task' && event.content.canceled == true) {
      return
    }

    if (config.type == 'stage' && event.content.canceled == true) {
      return
    }

    // TODO (lpollo): why do we have a 'CANCELED' status and a canceled property, which are prime for inconsistency?
    if (isExecution(config.type) &&
      (event.content.execution?.status == 'CANCELED' || event.content.execution?.canceled == true)) {
      return
    }

    // send application level notification

    String application = event.details.application

    def sendRequests = []

    // pipeline level
    if (isExecution(config.type)) {
      event.content?.execution?.notifications?.each { notification ->
        String key = getNotificationType()
        if (notification.type == key && notification?.when?.contains("${config.type}.$status".toString())) {
          sendRequests << notification
        }
      }
    }

    // stage level configurations
    if (config.type == 'stage') {
      boolean isSynthetic = event.content?.isSynthetic?: event.content?.context?.stageDetails?.isSynthetic
      if (event.content?.context?.sendNotifications && ( !isSynthetic ) ) {
        event.content?.context?.notifications?.each { notification ->
          String key = getNotificationType()
          if (notification.type == key && notification?.when?.contains("${config.type}.$status".toString())) {
            sendRequests << notification
          }
        }
      }
    }

    sendRequests.each { notification ->
      try {
        sendNotifications(notification, application, event, config, status)
      } catch (Exception e) {
        log.error('failed to send {} message ', notificationType ,e)
      }
    }
  }

  private boolean isExecution(String type) {
    return type == "pipeline" || type == "orchestration"
  }

  abstract String getNotificationType()

  abstract void sendNotifications(Map notification, String application, Event event, Map config, String status)

}
