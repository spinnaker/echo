/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents;

import dev.cdevents.CDEvents;
import dev.cdevents.constants.CDEventConstants;
import dev.cdevents.events.TaskRunFinishedCDEvent;
import io.cloudevents.CloudEvent;
import java.net.URI;

public class CDEventTaskRunFinished extends CDEventCreator {

  private String subjectTaskName;
  private String subjectPipelineRunId;
  private String subjectError;

  public CDEventTaskRunFinished(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    super(spinnakerUrl, executionId, spinnakerUrl, executionUrl);
    this.subjectTaskName = executionName;
    this.subjectPipelineRunId = executionId;
    this.subjectError = status;
  }

  public String getSubjectTaskName() {
    return subjectTaskName;
  }

  public void setSubjectTaskName(String subjectTaskName) {
    this.subjectTaskName = subjectTaskName;
  }

  public String getSubjectPipelineRunId() {
    return subjectPipelineRunId;
  }

  public void setSubjectPipelineRunId(String subjectPipelineRunId) {
    this.subjectPipelineRunId = subjectPipelineRunId;
  }

  public String getSubjectError() {
    return subjectError;
  }

  public void setSubjectError(String subjectError) {
    this.subjectError = subjectError;
  }

  @Override
  public CloudEvent createCDEvent() {
    TaskRunFinishedCDEvent cdEvent = new TaskRunFinishedCDEvent();
    cdEvent.setSource(URI.create(getSource()));
    cdEvent.setSubjectId(getSubjectId());
    cdEvent.setSubjectSource(URI.create(getSubjectSource()));
    cdEvent.setSubjectTaskName(getSubjectTaskName());
    cdEvent.setSubjectUrl(URI.create(getSubjectUrl()));
    cdEvent.setSubjectErrors(getSubjectError());
    cdEvent.setSubjectPipelineRunId(getSubjectPipelineRunId());
    if (getSubjectError().equals("complete")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.SUCCESS);
    } else if (getSubjectError().equals("failed")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.FAILURE);
    }

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
