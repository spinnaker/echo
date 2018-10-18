/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Triggers pipelines in _Orca_ when a user manually starts a pipeline.
 */
@Slf4j
@Component
public class ManualEventMonitor implements TriggerEventHandler {

  public static final String MANUAL_TRIGGER_TYPE = "manual";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(ManualEvent.TYPE);
  }

  @Override
  public ManualEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, ManualEvent.class);
  }

  public List<Pipeline> getMatchingPipelines(final TriggerEvent event, List<Pipeline> pipelines) {
    return pipelines.stream()
      .map(p -> withMatchingTrigger(event, p))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private Optional<Pipeline> withMatchingTrigger(final TriggerEvent event, Pipeline pipeline) {
    if (pipeline.isDisabled()) {
      return Optional.empty();
    } else {
      ManualEvent manualEvent = (ManualEvent) event;
      Trigger trigger = manualEvent.getContent().getTrigger();
      return Stream.of(trigger)
        .filter(matchTriggerFor(event, pipeline))
        .findFirst()
        .map(buildTrigger(pipeline, event));
    }
  }

  private Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    ManualEvent manualEvent = (ManualEvent) event;
    return trigger -> {
      List<Map<String, Object>> notifications = buildNotifications(pipeline.getNotifications(),
        manualEvent.getContent().getTrigger().getNotifications());
      return pipeline
        .withTrigger(manualEvent.getContent().getTrigger().atPropagateAuth(true))
        .withNotifications(notifications);
    };
  }

  private List<Map<String, Object>> buildNotifications(List<Map<String, Object>> pipelineNotifications, List<Map<String, Object>> triggerNotifications) {
    List<Map<String, Object>> notifications = new ArrayList<>();
    if (pipelineNotifications != null) {
      notifications.addAll(pipelineNotifications);
    }
    if (triggerNotifications != null) {
      notifications.addAll(triggerNotifications);
    }
    return notifications;
  }

  private Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    ManualEvent manualEvent = (ManualEvent) event;
    String application = manualEvent.getContent().getApplication();
    String nameOrId = manualEvent.getContent().getPipelineNameOrId();

    return trigger -> pipeline.getApplication().equals(application) && (
      pipeline.getName().equals(nameOrId) || pipeline.getId().equals(nameOrId)
    );
  }
}
