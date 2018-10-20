/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseTriggerEventHandler<T extends TriggerEvent> implements TriggerEventHandler<T> {
  private Registry registry;

  public BaseTriggerEventHandler(Registry registry) {
    this.registry = registry;
  }

  public List<Pipeline> getMatchingPipelines(final T event, List<Pipeline> pipelines) {
    if (isSuccessfulTriggerEvent(event)) {
      return pipelines.stream()
        .map(p -> withMatchingTrigger(event, p))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    } else {
      return new ArrayList<>();
    }
  }

  private Optional<Pipeline> withMatchingTrigger(final T event, Pipeline pipeline) {
    if (pipeline.getTriggers() == null || pipeline.isDisabled()) {
      return Optional.empty();
    } else {
      try {
        return pipeline.getTriggers()
          .stream()
          .filter(this::isValidTrigger)
          .filter(matchTriggerFor(event, pipeline))
          .findFirst()
          .map(buildTrigger(pipeline, event));
      } catch (Exception e) {
        onSubscriberError(e);
        return Optional.empty();
      }
    }
  }

  private void onSubscriberError(Throwable error) {
    log.error("Subscriber raised an error processing pipeline", error);
    registry.counter("trigger.errors").increment();
  }

  protected abstract boolean isSuccessfulTriggerEvent(T event);

  protected abstract Predicate<Trigger> matchTriggerFor(final T event, final Pipeline pipeline);

  protected abstract Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, T event);

  protected abstract boolean isValidTrigger(final Trigger trigger);
}
