/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.pipelinetriggers.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.TriggerEventHandler;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Triggers pipelines on _Orca_ when a trigger-enabled build completes successfully.
 */
@Slf4j
public class TriggerMonitor<T extends TriggerEvent> implements EchoEventListener {
  protected final PipelineInitiator pipelineInitiator;
  protected final Registry registry;
  protected final ObjectMapper objectMapper = new ObjectMapper();
  protected final PipelineCache pipelineCache;
  protected final TriggerEventHandler<T> eventHandler;

  private void validateEvent(Event event) {
    if (event.getDetails() == null) {
      throw new IllegalArgumentException("Event details required by the event monitor.");
    } else if (event.getDetails().getType() == null) {
      throw new IllegalArgumentException("Event details type required by the event monitor.");
    }
  }

  public TriggerMonitor(@NonNull PipelineCache pipelineCache,
                        @NonNull PipelineInitiator pipelineInitiator,
                        @NonNull Registry registry,
                        @NonNull TriggerEventHandler<T> eventHandler) {
    this.pipelineInitiator = pipelineInitiator;
    this.registry = registry;
    this.pipelineCache = pipelineCache;
    this.eventHandler = eventHandler;
  }

  public void processEvent(Event event) {
    validateEvent(event);
    if (!eventHandler.handleEventType(event.getDetails().getType())) {
      return;
    }
    T triggerEvent = eventHandler.convertEvent(event);
    onEchoResponse(triggerEvent);
    triggerMatchingPipelines(triggerEvent, pipelineCache.getPipelinesSync());
  }

  private void onEchoResponse(final TriggerEvent event) {
    registry.gauge("echo.events.per.poll", 1);
  }

  private void triggerMatchingPipelines(final T event, List<Pipeline> pipelines) {
    onEventProcessed(event);
    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);
    matchingPipelines.forEach(p -> {
      onMatchingPipeline(p);
      pipelineInitiator.startPipeline(p);
    });
  }

  private void onMatchingPipeline(Pipeline pipeline) {
    log.info("Found matching pipeline {}:{}", pipeline.getApplication(), pipeline.getName());
    emitMetricsOnMatchingPipeline(pipeline);
  }

  private void onEventProcessed(final TriggerEvent event) {
    registry.counter("echo.events.processed").increment();
  }

  private void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    Id id = registry.createId("pipelines.triggered")
      .withTag("monitor", getClass().getSimpleName())
      .withTag("application", pipeline.getApplication())
      .withTags(eventHandler.getAdditionalTags(pipeline));
    registry.counter(id).increment();
  }
}
