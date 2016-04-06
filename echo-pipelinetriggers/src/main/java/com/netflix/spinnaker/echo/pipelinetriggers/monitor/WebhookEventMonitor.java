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
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent;
import com.netflix.spinnaker.echo.model.trigger.TriggerEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import lombok.NonNull;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Action1;

import java.util.function.Function;
import java.util.function.Predicate;

@Component @Slf4j
public class WebhookEventMonitor extends TriggerMonitor {

  public static final String TRIGGER_TYPE = "webhook";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PipelineCache pipelineCache;

  @Autowired
  public WebhookEventMonitor(@NonNull PipelineCache pipelineCache,
                             @NonNull Action1<Pipeline> subscriber,
                             @NonNull Registry registry) {
    super(subscriber, registry);
    this.pipelineCache = pipelineCache;
  }

  @Override
  public void processEvent(Event event) {
    super.validateEvent(event);
    if (event.getDetails().getType() == null ||
        !event.getDetails().getType().equalsIgnoreCase(WebhookEvent.TYPE)) {
      return;
    }

    WebhookEvent webhookEvent = objectMapper.convertValue(event, WebhookEvent.class);

    Observable.just(webhookEvent)
      .doOnNext(this::onEchoResponse)
      .subscribe(triggerEachMatchFrom(pipelineCache.getPipelines()));
  }

  @Override
  protected boolean isSuccessfulTriggerEvent(final TriggerEvent event) {
    return true;
  }

  @Override
  protected Function<Trigger, Pipeline> buildTrigger(Pipeline pipeline, TriggerEvent event) {
    WebhookEvent webhookEvent = (WebhookEvent) event;
    return trigger -> pipeline.withTrigger(trigger.inCategory(
        webhookEvent.getDetails().getType().toLowerCase(),
        webhookEvent.getDetails().getCategory(),
        webhookEvent.getDetails().getSource()
    ));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    return trigger.isEnabled() &&
      (
        (TRIGGER_TYPE.equals(trigger.getType()) &&
          trigger.getType() != null &&
          trigger.getCategory() != null &&
          trigger.getSource() != null)
      );

  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    WebhookEvent webhookEvent = (WebhookEvent) event;
    String type = webhookEvent.getDetails().getType();
    String category = webhookEvent.getDetails().getCategory();
    String source = webhookEvent.getDetails().getSource();

    log.info("In matchTriggerFor " +  type);
    log.info("In matchTriggerFor " +  category);
    log.info("In matchTriggerFor " +  source);
    log.info("In matchTriggerFor " +  webhookEvent.getContent());

    return trigger -> trigger.getType().equalsIgnoreCase(TRIGGER_TYPE) &&
      trigger.getCategory().equals(category) &&
      trigger.getSource().equals(source);
  }

  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName());
    id.withTag("source", pipeline.getTrigger().getSource())
      .withTag("type", pipeline.getTrigger().getType())
      .withTag("category", pipeline.getTrigger().getCategory());
    registry.counter(id).increment();
  }
}

