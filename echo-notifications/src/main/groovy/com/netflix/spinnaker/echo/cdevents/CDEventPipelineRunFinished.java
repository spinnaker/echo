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
import dev.cdevents.events.PipelineRunFinishedCDEvent;
import io.cloudevents.CloudEvent;
import java.net.URI;

public class CDEventPipelineRunFinished implements CDEventCreator {

  private String source;
  private String subjectId;
  private String subjectSource;
  private String subjectPipelineName;
  private String subjectUrl;

  private String subjectError;

  public CDEventPipelineRunFinished(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    this.source = spinnakerUrl;
    this.subjectId = executionId;
    this.subjectSource = spinnakerUrl;
    this.subjectPipelineName = executionName;
    this.subjectUrl = executionUrl;
    this.subjectError = status;
  }

  @Override
  public CloudEvent createCDEvent() {
    PipelineRunFinishedCDEvent cdEvent = new PipelineRunFinishedCDEvent();
    cdEvent.setSource(URI.create(source));
    cdEvent.setSubjectId(subjectId);
    cdEvent.setSubjectSource(URI.create(subjectSource));
    cdEvent.setSubjectPipelineName(subjectSource);
    cdEvent.setSubjectUrl(URI.create(subjectUrl));
    cdEvent.setSubjectErrors(subjectError);

    if (subjectError.equals("complete")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.SUCCESS);
    } else if (subjectError.equals("failed")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.FAILURE);
    }

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
