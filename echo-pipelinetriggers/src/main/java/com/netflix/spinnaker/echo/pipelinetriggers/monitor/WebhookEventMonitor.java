/*
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

import static com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher.isConstraintInPayload;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.echo.model.trigger.PubsubEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.ArtifactMatcher;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Action1;

@Component @Slf4j
public class WebhookEventMonitor extends TriggerMonitor {

  public static final String TRIGGER_TYPE = "webhook";

  private static final TypeReference<List<Artifact>> ARTIFACT_LIST =
      new TypeReference<List<Artifact>>() {};

  @Autowired
  public WebhookEventMonitor(@NonNull PipelineCache pipelineCache,
                             @NonNull Action1<Pipeline> subscriber,
                             @NonNull Registry registry) {
    super(pipelineCache, subscriber, registry);
  }

  @Override
  protected boolean handleEventType(String eventType) {
    return eventType != null;
  }


  @Override
  protected WebhookEvent convertEvent(Event event) {
    return objectMapper.convertValue(event, WebhookEvent.class);
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    return true;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    WebhookEvent webhookEvent = objectMapper.convertValue(event, WebhookEvent.class);
    Map content = webhookEvent.getContent();
    Map payload = webhookEvent.getPayload();
    Map parameters = content.containsKey("parameters") ? (Map) content.get("parameters") : new HashMap();
    List<Artifact> artifacts = objectMapper.convertValue(content.getOrDefault("artifacts", emptyList()), ARTIFACT_LIST);

    return trigger -> pipeline
        .withReceivedArtifacts(artifacts)
        .withTrigger(trigger.atParameters(parameters)
          .atPayload(payload)
          .atEventId(event.getEventId()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() && TRIGGER_TYPE.equals(trigger.getType());
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event, final Pipeline pipeline) {
    WebhookEvent webhookEvent = objectMapper.convertValue(event, WebhookEvent.class);
    final String type = webhookEvent.getDetails().getType();
    final String source = webhookEvent.getDetails().getSource();
    final Map content = webhookEvent.getContent();
    final List<Artifact> messageArtifacts = objectMapper.convertValue(content.getOrDefault("artifacts", emptyList()), ARTIFACT_LIST);

    return trigger ->
        trigger.getType() != null && trigger.getType().equalsIgnoreCase(type) &&
        trigger.getSource() != null && trigger.getSource().equals(source) &&
        (
            // The Constraints in the Trigger could be null. That's OK.
            trigger.getPayloadConstraints() == null ||

            // If the Constraints are present, check that there are equivalents in the webhook payload.
            (  trigger.getPayloadConstraints() != null &&
               isConstraintInPayload(trigger.getPayloadConstraints(), event.getPayload())
            )

        ) &&
        // note this returns true when no artifacts are expected
        ArtifactMatcher.anyArtifactsMatchExpected(messageArtifacts, trigger, pipeline);
  }

  @Override
  protected void emitMetricsOnMatchingPipeline(Pipeline pipeline) {
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("monitor", getClass().getSimpleName());
    id.withTag("type", pipeline.getTrigger().getType());
    registry.counter(id).increment();
  }
}

