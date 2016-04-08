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

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

@Component @Slf4j
public class WebhookEventMonitor extends TriggerMonitor {

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
    if (event.getDetails().getType() == null) {
      return;
    }

    log.info("In processEvent " + event);

    /* Need to create WebhookEvent, since TriggerEvent is abstract */
    WebhookEvent webhookEvent = objectMapper.convertValue(event, WebhookEvent.class);
    webhookEvent.setDetails(event.getDetails());
    webhookEvent.setPayload(event.getContent());

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
    log.info("In buildTrigger " + event);
    log.info("In buildTrigger " + event.getDetails().getType());
    log.info("In buildTrigger " + event.getDetails().getSource());
    log.info("In buildTrigger " + event.getPayload());
    return trigger -> pipeline.withTrigger(trigger.atExtras(event.getPayload()));
  }

  @Override
  protected boolean isValidTrigger(final Trigger trigger) {
    boolean valid =  trigger.isEnabled() &&
      (
          trigger.getType() != null &&
          trigger.getSource() != null
      );

    log.info("In isValidTrigger " +  trigger.getType());
    log.info("In isValidTrigger " +  trigger.getSource());
    log.info("In isValidTrigger " +  trigger.getExtras());
    log.info("In isValidTrigger " +  valid);

    return valid;
  }

  @Override
  protected Predicate<Trigger> matchTriggerFor(final TriggerEvent event) {
    String type = event.getDetails().getType();
    String source = event.getDetails().getSource();

    log.info("In matchTriggerFor " +  type);
    log.info("In matchTriggerFor " +  source);
    log.info("In matchTriggerFor " +  event.getPayload());

    return trigger ->
      trigger.getType().equals(type) &&
      trigger.getSource().equals(source) &&
        (
          // The Extras in the Trigger could be null. That's OK.
          trigger.getExtras() == null ||

            // If the Extras are present, check that there are equivalents in the webhook payload.
            (  trigger.getExtras() != null &&
               isExtraInPayload(trigger.getExtras(), event.getPayload())
            )

        );

  }

  /**
   * Check that there is an item in the payload for each extra declared in a Trigger.
   * @param extras A map of extras configured in the Trigger (eg, created in Deck).
   * @param payload A map of the payload contents POST'd in the Webhook.
   * @return Whether every key in the extras map is represented in the payload.
     */
  protected boolean isExtraInPayload(final Map extras, final Map payload) {
    for (Object key : extras.keySet()){
      if (!payload.containsKey(key) || payload.get(key) == null) {
        log.info("Webhook trigger ignored. Item " + key.toString() + " was not found in payload");
        return false;
      }
    }
    return true;
  }


  protected void onMatchingPipeline(Pipeline pipeline) {
    super.onMatchingPipeline(pipeline);
    val id = registry.createId("pipelines.triggered")
      .withTag("application", pipeline.getApplication())
      .withTag("name", pipeline.getName());
    id.withTag("source", pipeline.getTrigger().getSource())
      .withTag("type", pipeline.getTrigger().getType());
    registry.counter(id).increment();
  }
}

