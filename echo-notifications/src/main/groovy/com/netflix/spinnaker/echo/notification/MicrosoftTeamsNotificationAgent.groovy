/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.notification;

import com.netflix.spinnaker.echo.microsoftteams.api.MicrosoftTeamsSection
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsMessage
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsService
import com.netflix.spinnaker.echo.api.events.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import retrofit.client.Response

import static org.apache.commons.lang3.text.WordUtils.capitalize

@Slf4j
@ConditionalOnProperty("microsoftteams.enabled")
@Service
class MicrosoftTeamsNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  MicrosoftTeamsService teamsService

  @Override
  public String getNotificationType() {
    return "microsoftteams";
  }

  @Override
  void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    log.info('Building Microsoft Teams notification')

    Integer buildNumber
    Map context = event.content?.context ?: [:]
    String cardTitle = "${capitalize(config.type)} ${status} for ${application.toUpperCase()}"
    String customMessage
    String eventName
    String executionUrl = "${spinnakerUrl}/#/applications/${application}/${config.type == 'stage' ? 'executions/details' : config.link }/${event.content?.execution?.id}"
    String executionDescription = event.content?.execution?.description
    String executionName = event.content?.execution?.name
    String message
    String summary

    if (config.type == 'pipeline' || config.type == 'stage') {
      if (event.content?.execution?.trigger?.buildInfo?.url) {
        executionUrl = event.content?.execution?.trigger?.buildInfo?.url
        buildNumber = (Integer)event.content.execution.trigger.buildInfo.number
      }
    }

    if (config.type == 'stage') {
      eventName = event.content.name ?: context.stageDetails.name
      summary = """Stage $eventName for ${application}'s ${event.content?.execution?.name} pipeline """
    } else if (config.type == 'pipeline') {
      summary = """${application}'s ${event.content?.execution?.name} pipeline """
    } else {
      summary = """${application}'s ${event.content?.execution?.id} task """
    }

    summary += """${status == 'starting' ? 'is' : 'has'} ${status == 'complete' ? 'completed successfully' : status}"""

    if (preference.message?."$config.type.$status"?.text) {
      message = preference.message."$config.type.$status".text
    }

    customMessage = preference.customMessage ?: event.content?.context?.customMessage
    if (customMessage) {
      customMessage = customMessage
        .replace("{{executionId}}", (String) event.content.execution?.id ?: "")
        .replace("{{link}}", link ?: "")
    }

    MicrosoftTeamsMessage teamsMessage = new MicrosoftTeamsMessage(summary, status);
    MicrosoftTeamsSection section = teamsMessage.createSection(config.type, cardTitle);

    section.setApplicationName(application);
    section.setBuildNumber(buildNumber);
    section.setCustomMessage(customMessage);
    section.setDescription(executionDescription);
    section.setExecutionName(executionName);
    section.setEventName(eventName);
    section.setMessage(message);
    section.setStatus(status);
    section.setSummary(summary);
    section.setPotentialAction(executionUrl, null);

    teamsMessage.addSection(section);

    log.info('Sending Microsoft Teams notification')
    String baseUrl = "https://outlook.office.com/webhook/";
    String completeLink = preference.address;
    String partialWebhookURL = completeLink.substring(baseUrl.length());
    Response response = teamsService.sendMessage(partialWebhookURL, teamsMessage);

    log.info("Received response from Microsoft Teams Webhook  : {} {} for execution id {}. {}",
      response?.status, response?.reason, event.content?.execution?.id, response?.body)
  }
}
