/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.cdevents.CDEventsBuilderService;
import com.netflix.spinnaker.echo.cdevents.CDEventsSenderService;
import dev.cdevents.exception.CDEventsException;
import io.cloudevents.CloudEvent;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit.mime.TypedByteArray;

@Slf4j
@ConditionalOnProperty("cdevents.enabled")
@Service
public class CDEventsNotificationAgent extends AbstractEventNotificationAgent {
  @Autowired CDEventsBuilderService cdEventsBuilderService;
  @Autowired CDEventsSenderService cdEventsSenderService;

  @Override
  public String getNotificationType() {
    return "cdevents";
  }

  @Override
  public void sendNotifications(
      Map<String, Object> preference,
      String application,
      Event event,
      Map<String, String> config,
      String status) {
    log.info("Sending CDEvents notification..");

    String executionId =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("id"))
            .orElse(null);
    String cdEventType =
        Optional.ofNullable(preference).map(p -> (String) p.get("cdEventType")).orElse(null);
    String eventsBrokerUrl =
        Optional.ofNullable(preference).map(p -> (String) p.get("address")).orElse(null);

    try {
      CloudEvent ceToSend =
          cdEventsBuilderService.createCDEvent(
              preference, application, event, config, status, getSpinnakerUrl());
      log.info(
          "Sending CDEvent {} notification to events broker url {}", cdEventType, eventsBrokerUrl);
      HttpURLConnection response =
          cdEventsSenderService.sendCDEvent(ceToSend, new URL(eventsBrokerUrl));
      if (response != null) {
        log.info(
            "Received response from events broker : {} {} for execution id {}. {}",
            response.getResponseCode(),
            response.getResponseMessage(),
            executionId,
            new String(((TypedByteArray) response.getContent()).getBytes()));
      }

    } catch (Exception e) {
      log.error("Exception occurred while sending CDEvent {}", e);
      throw new CDEventsException("Exception occurred while sending CDEvent", e);
    }
  }
}
