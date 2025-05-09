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

package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.common.Header;
import java.util.Collection;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface OrcaService {

  @POST("orchestrate")
  Call<TriggerResponse> trigger(@Body Pipeline pipeline);

  @POST("fail")
  Call<ResponseBody> recordFailure(@Body Pipeline pipeline);

  @POST("plan")
  Call<Map> plan(@Body Map pipelineConfig, @Query("resolveArtifacts") boolean resolveArtifacts);

  @POST("v2/pipelineTemplates/plan")
  Call<Map<String, Object>> v2Plan(@Body Map pipelineConfig);

  @GET("pipelines")
  Call<Collection<PipelineResponse>> getLatestPipelineExecutions(
      @Query("pipelineConfigIds") Collection<String> pipelineIds, @Query("limit") Integer limit);

  class TriggerResponse {
    // workaround for not having a constant value for reference via annotation:
    static final String X_SPINNAKER_USER = "X-SPINNAKER-USER";

    static {
      // TODO(cf): if this actually fails you will all thank me I swear
      if (!X_SPINNAKER_USER.equals(Header.USER.getHeader())) {
        throw new IllegalStateException("The header changed. Why did the header change. Whyyy");
      }
    }

    private String ref;

    public TriggerResponse() {
      // do nothing
    }

    public String getRef() {
      return ref;
    }
  }

  @Getter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  class PipelineResponse {
    private String id;
    private String pipelineConfigId;
    private Long startTime;
    private OrcaExecutionStatus status;
    private Trigger trigger;
  }
}
