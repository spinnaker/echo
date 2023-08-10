/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Event listener for echo events */
@Component
@ConditionalOnProperty("rest.enabled")
@Getter
@Setter
public class RestEventListener implements EventListener {

  private static final Logger log = LoggerFactory.getLogger(RestEventListener.class);

  private ObjectMapper mapper = EchoObjectMapper.getInstance();

  private final RestUrls restUrls;
  private final RestEventTemplateEngine restEventTemplateEngine;
  private final RestEventService restEventService;
  private final Registry registry;

  @Value("${rest.default-event-name:spinnaker_events}")
  private String eventName;

  @Value("${rest.default-field-name:payload}")
  private String fieldName;

  @Value("${rest.circuit-breaker-enabled:false}")
  private boolean circuitBreakerEnabled;

  @Autowired
  public RestEventListener(
      RestUrls restUrls,
      RestEventTemplateEngine restEventTemplateEngine,
      RestEventService restEventService,
      Registry registry) {
    this.restUrls = restUrls;
    this.restEventTemplateEngine = restEventTemplateEngine;
    this.restEventService = restEventService;
    this.registry = registry;
  }

  @Override
  public void processEvent(Event event) {
    restUrls
        .getServices()
        .forEach(
            (service) -> {
              try {
                Map<String, Object> eventMap = transformEventToMap(event, service);
                if (circuitBreakerEnabled) {
                  restEventService.sendEventWithCircuitBreaker(eventMap, service);
                } else {
                  restEventService.sendEvent(eventMap, service);
                }
              } catch (Exception e) {
                handleError(event, service, e);
              }
            });
  }

  /**
   * Transforms the event into a map to be sent in the REST request.
   *
   * @param event The event to be transformed.
   * @param service The service for which the transformation is done.
   * @return The transformed event as a map.
   */
  private Map<String, Object> transformEventToMap(Event event, RestUrls.Service service)
      throws JsonProcessingException {
    Map<String, Object> eventMap = mapper.convertValue(event, Map.class);

    if (service.getConfig().getFlatten()) {
      eventMap.put("content", mapper.writeValueAsString(eventMap.get("content")));
      eventMap.put("details", mapper.writeValueAsString(eventMap.get("details")));
    }

    if (service.getConfig().getWrap()) {
      if (service.getConfig().getTemplate() != null) {
        eventMap = restEventTemplateEngine.render(service.getConfig().getTemplate(), eventMap);
      } else {
        Map<String, Object> m = new HashMap<>();

        m.put(
            "eventName", StringUtils.defaultString(service.getConfig().getEventName(), eventName));

        m.put(StringUtils.defaultString(service.getConfig().getFieldName(), fieldName), eventMap);

        eventMap = m;
      }
    }

    return eventMap;
  }

  /**
   * Handles errors that occur while processing or sending the event.
   *
   * @param event The event that caused the error.
   * @param service The service to which the event was being sent.
   * @param exception The exception that occurred.
   */
  private void handleError(Event event, RestUrls.Service service, Exception exception) {
    if (event != null
        && event.getDetails() != null
        && event.getDetails().getSource() != null
        && event.getDetails().getType() != null) {
      log.error(
          "Could not send event source={}, type={} to {}.\n Event details: {}",
          event.getDetails().getSource(),
          event.getDetails().getType(),
          service.getConfig().getUrl(),
          event,
          exception);
    } else {
      log.error("Could not send event.", exception);
    }

    registry.counter("event.send.errors", "exception", exception.getClass().getName()).increment();
  }
}
