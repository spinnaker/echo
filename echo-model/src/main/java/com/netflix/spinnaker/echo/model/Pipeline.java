/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Wither;

@JsonDeserialize(builder = Pipeline.PipelineBuilder.class)
@Builder
@Wither
@ToString(
    of = {"application", "name", "id"},
    includeFieldNames = false)
@Value
public class Pipeline {
  @JsonProperty @NonNull String application;

  Object config;

  @JsonProperty @NonNull String name;

  @JsonProperty String id;

  @JsonProperty String executionId;

  @JsonProperty String executionEngine;

  @JsonProperty boolean parallel;

  @JsonProperty boolean disabled;

  @JsonProperty boolean limitConcurrent;

  @JsonProperty boolean keepWaitingPipelines;

  @JsonProperty boolean plan;

  @JsonProperty boolean respectQuietPeriod;

  @JsonProperty List<Trigger> triggers;

  @JsonProperty String type;

  @JsonProperty String schema;

  @JsonProperty Object template;

  @JsonProperty List<Map<String, Object>> stages;

  @JsonProperty List<Map<String, Object>> notifications;

  @JsonProperty List<Artifact> receivedArtifacts;

  @JsonProperty List<ExpectedArtifact> expectedArtifacts;

  @JsonProperty List<Map<String, Object>> parameterConfig;

  @JsonProperty Object appConfig;

  Trigger trigger;

  @JsonPOJOBuilder(withPrefix = "")
  public static final class PipelineBuilder {
    @JsonProperty("config")
    private void setConfig(Map<String, Object> config) {
      if (config != null) {
        this.config = config;
        schema = (String) config.get("schema");
      }
    }

    @JsonProperty()
    Object getConfig() {
      return this.config;
    }
  }
}
