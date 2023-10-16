/*
 * Copyright 2023 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.cdevents;

import dev.cdevents.CDEvents;
import dev.cdevents.constants.CDEventConstants;
import dev.cdevents.events.TaskRunFinishedCDEvent;
import io.cloudevents.CloudEvent;
import java.net.URI;

public class CDEventTaskRunFinished implements CDEventCreator {

  private String source;
  private String subjectId;
  private String subjectSource;
  private String subjectTaskName;
  private String subjectUrl;
  private String subjectPipelineRunId;
  private String subjectError;

  public CDEventTaskRunFinished(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    this.source = spinnakerUrl;
    this.subjectId = executionId;
    this.subjectSource = spinnakerUrl;
    this.subjectTaskName = executionName;
    this.subjectUrl = executionUrl;
    this.subjectPipelineRunId = executionId;
    this.subjectError = status;
  }

  @Override
  public CloudEvent createCDEvent() {
    TaskRunFinishedCDEvent cdEvent = new TaskRunFinishedCDEvent();
    cdEvent.setSource(URI.create(source));

    cdEvent.setSubjectId(subjectId);
    cdEvent.setSubjectSource(URI.create(source));
    cdEvent.setSubjectTaskName(subjectTaskName);
    cdEvent.setSubjectUrl(URI.create(subjectUrl));
    cdEvent.setSubjectErrors(subjectError);
    cdEvent.setSubjectPipelineRunId(subjectPipelineRunId);
    if (subjectError.equals("complete")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.SUCCESS);
    } else if (subjectError.equals("failed")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.FAILURE);
    }

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
