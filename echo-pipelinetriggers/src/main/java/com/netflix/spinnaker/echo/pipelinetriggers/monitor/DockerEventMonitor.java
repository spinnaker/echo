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
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

@Component
public class DockerEventMonitor extends TriggerMonitor {

  public static final String TRIGGER_TYPE = "docker";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public DockerEventMonitor(@NonNull PipelineCache pipelineCache,
                            @NonNull Action1<Pipeline> subscriber,
                            @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (!event.getDetails().getType().equalsIgnoreCase(DockerEvent.TYPE)) {
      return;
    }

    DockerEvent dockerEvent = objectMapper.convertValue(event, DockerEvent.class);
    Observable.just(dockerEvent)
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    DockerEvent dockerEvent = (DockerEvent) event;
    // The event should always report a tag
    String tag = dockerEvent.getContent().getTag();
    return tag != null && !tag.isEmpty();
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    DockerEvent dockerEvent = (DockerEvent) event;
    return trigger -> pipeline.withTrigger(trigger.atTag(dockerEvent.getContent().getTag()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (TRIGGER_TYPE.equals(trigger.getType()) &&
          trigger.getRegistry() != null &&
          trigger.getRepository() != null)
      );
  }

  private boolean matchTags(String suppliedTag, String incomingTag) {
    try {
      // use matches to handle regex or basic string compare
      return incomingTag.matches(suppliedTag);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    DockerEvent dockerEvent = (DockerEvent) event;
    String registry = dockerEvent.getContent().getRegistry();
    String repository = dockerEvent.getContent().getRepository();
    String tag = dockerEvent.getContent().getTag();
    return trigger -> trigger.getType().equals(TRIGGER_TYPE) &&
      trigger.getRepository().equals(repository) &&
      trigger.getRegistry().equals(registry) &&
      ((trigger.getTag() == null && !tag.equals("latest"))
        || trigger.getTag() != null && matchTags(trigger.getTag(), tag));
  }

  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName());
    id.withTag("imageId", pipeline.getTrigger().getRegistry() + "/" +
      pipeline.getTrigger().getRepository() + ":" +
      pipeline.getTrigger().getTag());
    registry.counter(id).increment();
  }
}

