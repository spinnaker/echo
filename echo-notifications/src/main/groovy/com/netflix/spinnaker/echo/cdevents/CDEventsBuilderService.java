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

import com.netflix.spinnaker.echo.api.events.Event;
import dev.cdevents.CDEvents;
import dev.cdevents.constants.CDEventConstants;
import dev.cdevents.events.*;
import dev.cdevents.exception.CDEventsException;
import io.cloudevents.CloudEvent;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CDEventsBuilderService {

  public CloudEvent createCDEvent(
      Map<String, Object> preference,
      String application,
      Event event,
      Map<String, String> config,
      String status,
      String spinnakerUrl) {

    String configType = Optional.ofNullable(config).map(c -> (String) c.get("type")).orElse(null);
    String configLink = Optional.ofNullable(config).map(c -> (String) c.get("link")).orElse(null);

    String executionId =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("id"))
            .orElse(null);

    String executionUrl =
        String.format(
            "%s/#/applications/%s/%s/%s",
            spinnakerUrl,
            application,
            configType == "stage" ? "executions/details" : configLink,
            executionId);

    String executionName =
        Optional.ofNullable(event.content)
            .map(e -> (Map) e.get("execution"))
            .map(e -> (String) e.get("name"))
            .orElse(null);

    String cdEventsType =
        Optional.ofNullable(preference).map(p -> (String) p.get("cdEventsType")).orElse(null);

    CloudEvent ceToSend =
        buildCloudEventWithCDEventType(
            cdEventsType, executionId, executionUrl, executionName, spinnakerUrl, status);
    if (ceToSend == null) {
      log.error("Failed to created CDEvent with type {} as CloudEvent", cdEventsType);
      throw new CDEventsException("Failed to created CDEvent as CloudEvent");
    }
    return ceToSend;
  }

  private CloudEvent buildCloudEventWithCDEventType(
      String cdEventsType,
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    CloudEvent ceToSend = null;
    switch (cdEventsType) {
      case "dev.cdevents.pipelinerun.queued":
        ceToSend =
            createPipelineRunQueuedEvent(executionId, executionUrl, executionName, spinnakerUrl);
        break;
      case "dev.cdevents.pipelinerun.started":
        ceToSend =
            createPipelineRunStartedEvent(executionId, executionUrl, executionName, spinnakerUrl);
        break;
      case "dev.cdevents.pipelinerun.finished":
        ceToSend =
            createPipelineRunFinishedEvent(
                executionId, executionUrl, executionName, spinnakerUrl, status);
        break;
      case "dev.cdevents.taskrun.started":
        ceToSend =
            createTaskRunStartedEvent(executionId, executionUrl, executionName, spinnakerUrl);
        break;
      case "dev.cdevents.taskrun.finished":
        ceToSend =
            createTaskRunFinishedEvent(
                executionId, executionUrl, executionName, spinnakerUrl, status);
        break;
      default:
        throw new CDEventsException(
            "Invalid CDEvents Type " + cdEventsType + " provided to create CDEvent");
    }
    return ceToSend;
  }

  private CloudEvent createTaskRunFinishedEvent(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    TaskRunFinishedCDEvent cdEvent = new TaskRunFinishedCDEvent();
    cdEvent.setSource(URI.create(spinnakerUrl));

    cdEvent.setSubjectId(executionId);
    cdEvent.setSubjectSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectTaskName(executionName);
    cdEvent.setSubjectUrl(URI.create(executionUrl));
    cdEvent.setSubjectErrors(status);
    if (status.equals("complete")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.SUCCESS);
    } else if (status.equals("failed")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.FAILURE);
    }
    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }

  private CloudEvent createTaskRunStartedEvent(
      String executionId, String executionUrl, String executionName, String spinnakerUrl) {
    TaskRunStartedCDEvent cdEvent = new TaskRunStartedCDEvent();
    cdEvent.setSource(URI.create(spinnakerUrl));

    cdEvent.setSubjectId(executionId);
    cdEvent.setSubjectSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectTaskName(executionName);
    cdEvent.setSubjectUrl(URI.create(executionUrl));

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }

  private CloudEvent createPipelineRunFinishedEvent(
      String executionId,
      String executionUrl,
      String executionName,
      String spinnakerUrl,
      String status) {
    PipelineRunFinishedCDEvent cdEvent = new PipelineRunFinishedCDEvent();
    cdEvent.setSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectId(executionId);
    cdEvent.setSubjectSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectPipelineName(executionName);
    cdEvent.setSubjectUrl(URI.create(executionUrl));
    cdEvent.setSubjectErrors(status);

    if (status.equals("complete")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.SUCCESS);
    } else if (status.equals("failed")) {
      cdEvent.setSubjectOutcome(CDEventConstants.Outcome.FAILURE);
    }

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }

  private CloudEvent createPipelineRunStartedEvent(
      String executionId, String executionUrl, String executionName, String spinnakerUrl) {
    PipelineRunStartedCDEvent cdEvent = new PipelineRunStartedCDEvent();
    cdEvent.setSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectId(executionId);
    cdEvent.setSubjectSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectPipelineName(executionName);
    cdEvent.setSubjectUrl(URI.create(executionUrl));

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }

  private CloudEvent createPipelineRunQueuedEvent(
      String executionId, String executionUrl, String executionName, String spinnakerUrl) {
    PipelineRunQueuedCDEvent cdEvent = new PipelineRunQueuedCDEvent();
    cdEvent.setSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectId(executionId);
    cdEvent.setSubjectSource(URI.create(spinnakerUrl));
    cdEvent.setSubjectPipelineName(executionName);
    cdEvent.setSubjectUrl(URI.create(executionUrl));

    return CDEvents.cdEventAsCloudEvent(cdEvent);
  }
}
