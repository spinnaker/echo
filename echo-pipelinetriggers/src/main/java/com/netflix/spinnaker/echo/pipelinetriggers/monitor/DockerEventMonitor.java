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

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.anyArtifactsMatchExpected;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import lombok.NonNull;
import lombok.val;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Action1;

@Component
public class DockerEventMonitor extends TriggerMonitor {

  public static final String TRIGGER_TYPE = "docker";

  @Autowired
  public DockerEventMonitor(@NonNull PipelineCache pipelineCache,
                            @NonNull Action1<Pipeline> subscriber,
                            @NonNull Registry registry) {
    super(pipelineCache, subscriber, registry);
  }

  @Override
  protected boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(DockerEvent.TYPE);
  }

  @Override
  protected DockerEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, DockerEvent.class);
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    DockerEvent dockerEvent = (DockerEvent) event;
    // The event should always report a tag
    String tag = dockerEvent.getContent().getTag();
    return tag != null && !tag.isEmpty();
  }

  private static List<Artifact> getArtifacts(DockerEvent dockerEvent) {
    DockerEvent.Content content = dockerEvent.getContent();

    String name = content.getRegistry() + "/" + content.getRepository();
    String reference = name + ":" + content.getTag();
    return Collections.singletonList(Artifact.builder()
      .type("docker/image")
      .name(name)
      .version(content.getTag())
      .reference(reference)
      .build());
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    DockerEvent dockerEvent = (DockerEvent) event;

    return trigger -> pipeline.withTrigger(trigger.atTag(dockerEvent.getContent().getTag()).withEventId(event.getEventId()))
      .withReceivedArtifacts(getArtifacts(dockerEvent));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (TRIGGER_TYPE.equals(trigger.getType()) &&
          trigger.getAccount() != null &&
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
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    return trigger -> isMatchingTrigger((DockerEvent)event, trigger, pipeline);
  }

  private boolean isMatchingTrigger(DockerEvent dockerEvent, Trigger trigger, final Pipeline pipeline) {
    String account = dockerEvent.getContent().getAccount();
    String repository = dockerEvent.getContent().getRepository();
    String eventTag = dockerEvent.getContent().getTag();
    String triggerTagPattern = null;
    if (StringUtils.isNotBlank(trigger.getTag())) {
      triggerTagPattern = trigger.getTag().trim();
    }
    return trigger.getType().equals(TRIGGER_TYPE) &&
            trigger.getRepository().equals(repository) &&
            trigger.getAccount().equals(account) &&
            ((triggerTagPattern == null && !eventTag.equals("latest"))
              || triggerTagPattern != null && matchTags(triggerTagPattern, eventTag)) &&
            anyArtifactsMatchExpected(getArtifacts(dockerEvent), trigger, pipeline);
  }

  @Override
  protected void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    val id = registry.createId("pipelines.triggered")
      .withTag("monitor", getClass().getSimpleName())
      .withTag("application", pipeline.getApplication());
    registry.counter(id).increment();
  }
}

