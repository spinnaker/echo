/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.model.Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AbstractEventNotificationAgentSpec extends Specification {
  def subclassMock = Mock(AbstractEventNotificationAgent)
  @Subject def agent = new AbstractEventNotificationAgent() {
    @Override
    String getNotificationType() {
      return "fake"
    }

    @Override
    void sendNotifications(Map notification, String application, Event event, Map config, String status) {
      subclassMock.sendNotifications(notification, application, event, config, status)
    }
  }

  @Unroll
  def "sends notifications based on status and configuration"() {
    given:
    subclassMock.sendNotifications(*_) >> { notification, application, event, config, status -> }

    when:
    agent.processEvent(event)

    then:
    expectedNotifications * subclassMock.sendNotifications(*_)

    where:
    event                                                                                     || expectedNotifications
    // notifications ON, unknown event source
    fakePipelineEvent("whatever:pipeline:complete", 'SUCCEEDED', "pipeline.complete")         || 0
    // notifications ON, unknown event sub-type
    fakePipelineEvent("orca:whatever:whatever", 'SUCCEEDED', "pipeline.complete")             || 0
    // notifications OFF, succeeded pipeline
    fakePipelineEvent("orca:pipeline:complete", 'SUCCEEDED', null)                            || 0
    // notifications ON, succeeded pipeline and matching config
    fakePipelineEvent("orca:pipeline:complete", 'SUCCEEDED', "pipeline.complete")             || 1
    // notifications ON, succeeded pipeline and non-matching config
    fakePipelineEvent("orca:pipeline:complete", 'SUCCEEDED', "pipeline.failed")               || 0
    // notifications ON, failed pipeline and matching config
    fakePipelineEvent("orca:pipeline:failed", 'TERMINAL', "pipeline.failed")                  || 1
    // notifications ON, failed pipeline and non-matching config
    fakePipelineEvent("orca:pipeline:failed", 'TERMINAL', "pipeline.complete")                || 0
    // notifications ON, cancelled pipeline (should skip notifications)
    // note: this case is a bit convoluted as the event type is still set to "failed" by
    // orca for cancelled pipelines
    fakePipelineEvent("orca:pipeline:failed", 'CANCELED', 'pipeline.failed')                  || 0
    // notifications ON, another check for cancelled pipeline (should skip notifications)
    fakePipelineEvent("orca:pipeline:failed", 'WHATEVER', "pipeline.failed", [canceled: true]) || 0

    // TODO(lpollo): add cases for stages and tasksß
  }

  private def fakePipelineEvent(String type, String status, String notifyWhen, Map extraExecutionProps = [:]) {
    def eventProps = [details: [type: type],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: status
      ]]]

    if (notifyWhen) {
      eventProps.content.execution << [notifications: [[type: "fake", when: "${notifyWhen}"]]]
    }

    eventProps.content.execution << extraExecutionProps

    return new Event(eventProps)
  }

}
