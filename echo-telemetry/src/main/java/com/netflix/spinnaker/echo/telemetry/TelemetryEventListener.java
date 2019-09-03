/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.echo.telemetry;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.protobuf.util.JsonFormat;
import com.netflix.spinnaker.echo.config.TelemetryConfig;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.kork.proto.stats.Application;
import com.netflix.spinnaker.kork.proto.stats.Execution;
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance;
import com.netflix.spinnaker.kork.proto.stats.Stage;
import com.netflix.spinnaker.kork.proto.stats.Status;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import retrofit.mime.TypedString;

@Slf4j
@Component
@ConditionalOnProperty("telemetry.enabled")
public class TelemetryEventListener implements EchoEventListener {

  private static final Set<String> LOGGABLE_DETAIL_TYPES =
      ImmutableSet.of(
          "orca:orchestration:complete",
          "orca:orchestration:failed",
          "orca:pipeline:complete",
          "orca:pipeline:failed");

  private static final JsonFormat.Printer JSON_PRINTER =
      JsonFormat.printer().omittingInsignificantWhitespace();

  private final TelemetryService telemetryService;

  private final TelemetryConfig.TelemetryConfigProps telemetryConfigProps;

  @Autowired
  public TelemetryEventListener(
      TelemetryService telemetryService,
      TelemetryConfig.TelemetryConfigProps telemetryConfigProps) {
    this.telemetryService = telemetryService;
    this.telemetryConfigProps = telemetryConfigProps;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void processEvent(Event event) {
    try {
      if (event.getDetails() == null || event.getContent() == null) {
        log.debug("Telemetry not sent: Details or content not found in event");
        return;
      }

      String eventType = event.getDetails().getType();
      if (!LOGGABLE_DETAIL_TYPES.contains(eventType)) {
        log.debug("Telemetry not sent: type '{}' not whitelisted ", eventType);
        return;
      }

      String applicationId = event.getDetails().getApplication();
      if (applicationId == null || applicationId.isEmpty()) {
        log.debug("Application ID must be non-null and not empty");
        return;
      }

      Map<String, Object> execution = (Map<String, Object>) event.getContent().get("execution");
      if (execution == null || execution.isEmpty()) {
        log.debug("Missing execution from Event content.");
        return;
      }

      String hashedApplicationId = hash(applicationId);
      Execution.Type executionType =
          Execution.Type.valueOf(
              // TODO(ttomsu, louisjimenez): Add MPTv1 and v2 execution type detection.
              execution.getOrDefault("type", "UNKNOWN").toString().toUpperCase());
      Status executionStatus =
          Status.valueOf(execution.getOrDefault("status", "UNKNOWN").toString().toUpperCase());

      Map<String, Object> trigger =
          (Map<String, Object>) execution.getOrDefault("trigger", new HashMap<>());
      Execution.Trigger.Type triggerType =
          Execution.Trigger.Type.valueOf(
              trigger.getOrDefault("type", "UNKNOWN").toString().toUpperCase());

      List<Map> stages = (List<Map>) execution.getOrDefault("stages", new ArrayList<>());
      List<Stage> protoStages = stages.stream().map(this::toStage).collect(Collectors.toList());

      Execution.Builder executionBuilder =
          Execution.newBuilder()
              .setType(executionType)
              .setStatus(executionStatus)
              .setTrigger(Execution.Trigger.newBuilder().setType(triggerType))
              .addAllStages(protoStages);
      String executionId = execution.getOrDefault("id", "").toString();
      if (!executionId.isEmpty()) {
        executionBuilder.setId(hash(executionId));
      }
      Execution executionProto = executionBuilder.build();

      Application application = Application.newBuilder().setId(hashedApplicationId).build();

      SpinnakerInstance spinnakerInstance =
          SpinnakerInstance.newBuilder()
              .setId(telemetryConfigProps.getInstanceId())
              .setVersion(telemetryConfigProps.getSpinnakerVersion())
              .build();

      com.netflix.spinnaker.kork.proto.stats.Event loggedEvent =
          com.netflix.spinnaker.kork.proto.stats.Event.newBuilder()
              .setSpinnakerInstance(spinnakerInstance)
              .setApplication(application)
              .setExecution(executionProto)
              .build();

      String content = JSON_PRINTER.print(loggedEvent);
      telemetryService.log(new TypedJsonString(content));
      log.debug("Telemetry sent!");
    } catch (Exception e) {
      log.warn("Could not send Telemetry event {}", event, e);
    }
  }

  private Stage toStage(Map stage) {
    return Stage.newBuilder()
        .setType(stage.getOrDefault("type", "unknown").toString())
        .setStatus(Status.valueOf(stage.getOrDefault("status", "UNKNOWN").toString().toUpperCase()))
        .build();
  }

  private String hash(String clearText) {
    return Hashing.sha256().hashString(clearText, StandardCharsets.UTF_8).toString();
  }

  static class TypedJsonString extends TypedString {
    TypedJsonString(String body) {
      super(body);
    }

    @Override
    public String mimeType() {
      return "application/json";
    }

    @Override
    public String toString() {
      return new String(getBytes(), StandardCharsets.UTF_8);
    }
  }
}
