/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.artifacts;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.config.ArtifactEmitterUrls;
import com.netflix.spinnaker.echo.config.Service;
import com.netflix.spinnaker.echo.model.ArtifactEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * In an effort to move towards a more artifact centric workflow, this collector will accept
 *  artifacts from all over echo, and will publish them to any configured endpoint.
 *
 *  todo: emit via configurable pubsub as well as REST
 */
@Component
@ConditionalOnExpression("${artifact-emitter.enabled:false}")
public class ArtifactEmitter {
  private static final Logger log = LoggerFactory.getLogger(ArtifactEmitter.class);
  private final ArtifactEmitterUrls artifactEmitterUrls;
  private final ObjectMapper objectMapper;

  @Value("${artifact-emitter.defaultEventName:spinnaker_artifacts}")
  String eventName;

  @Value("${artifact-emitter.defaultFieldName:payload}")
  String fieldName;

  @Autowired
  public ArtifactEmitter(ArtifactEmitterUrls artifactEmitterUrls, ObjectMapper objectMapper) {
    this.artifactEmitterUrls = artifactEmitterUrls;
    this.objectMapper = objectMapper;
    log.info("Preparing to emit artifacts");
  }

  @EventListener
  @SuppressWarnings("unchecked")
  public void processEvent(ArtifactEvent event) {
    for (Service service: artifactEmitterUrls.services){
      try {
        Map eventAsMap = new HashMap();
        if (service.getConfig().isFlatten()) {
          eventAsMap.put("artifacts", objectMapper.writeValueAsString(event.getArtifacts()));
          eventAsMap.put("details", objectMapper.writeValueAsString(event.getDetails()));
        } else {
          eventAsMap = objectMapper.convertValue(event, Map.class);
        }

        Map sentEvent = new HashMap();
        String resolvedEventName = service.getConfig().getEventName() != null ? service.getConfig().getEventName() : eventName;
        sentEvent.put("eventName", resolvedEventName);
        String resolvedFieldName = service.getConfig().getFieldName() != null ? service.getConfig().getFieldName() : fieldName;
        sentEvent.put(resolvedFieldName, eventAsMap);

        service.getClient().recordEvent(sentEvent);
      } catch (Exception e) {
        log.error("Could not send event {} to {}", event, service.getConfig().getUrl(), e);
      }
    }
  }
}
