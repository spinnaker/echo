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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.services.IgorService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Post-processor that looks up build details from Igor if a pipeline's trigger is associated
 * with a build (ie, has a master and build number). Any later post-processor that wants to reference
 * this build information should set its @Order to greater than 1.
 */
@Component
@ConditionalOnProperty("igor.enabled")
public class BuildInfoPostProcessor implements PipelinePostProcessor{
  private IgorService igorService;

  BuildInfoPostProcessor(@NonNull IgorService igorService) {
    this.igorService = igorService;
  }

  public Pipeline processPipeline(Pipeline inputPipeline) {
    Trigger inputTrigger = inputPipeline.getTrigger();
    if (inputTrigger == null) {
      return inputPipeline;
    } else {
      Trigger augmentedTrigger = addBuildInfo(inputTrigger);
      return inputPipeline.withTrigger(augmentedTrigger);
    }
  }

  private Trigger addBuildInfo(@NonNull Trigger inputTrigger) {
    String master = inputTrigger.getMaster();
    Integer buildNumber = inputTrigger.getBuildNumber();
    String job = inputTrigger.getJob();
    String propertyFile = inputTrigger.getPropertyFile();

    Map<String, Object> buildInfo = null;
    Map<String, Object> properties = null;
    if (master != null && buildNumber != null && StringUtils.isNotEmpty(job)) {
      buildInfo = igorService.getBuild(buildNumber, master, job);
      if (StringUtils.isNotEmpty(propertyFile)) {
        properties = igorService.getPropertyFile(buildNumber, propertyFile, master, job);
      }
    }
    return inputTrigger.withBuildInfo(buildInfo).withProperties(properties);
  }

  public PostProcessorPriority priority() {
    return PostProcessorPriority.BUILD_INFO;
  }
}
