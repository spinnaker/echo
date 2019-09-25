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
    event                                                                                   || expectedNotifications
    // notifications ON, unknown event source
    new Event(details: [type: "whatever:pipeline:complete"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'SUCCEEDED',
        notifications: [[type: "fake", when: "pipeline.complete"]]
      ]])                                                                                   || 0
    // notifications ON, unknown event sub-type
    new Event(details: [type: "orca:whatever:whatever"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'SUCCEEDED',
        notifications: [[type: "fake", when: "pipeline.complete"]]
      ]])                                                                                   || 0
    // notifications OFF, succeeded pipeline
    new Event(details: [type: "orca:pipeline:complete"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'SUCCEEDED',
        notifications: []
      ]])                                                                                   || 0
    // notifications ON, succeeded pipeline and matching config
    new Event(details: [type: "orca:pipeline:complete"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'SUCCEEDED',
        notifications: [[type: "fake", when: "pipeline.complete"]]
      ]])                                                                                   || 1
    // notifications ON, succeeded pipeline and non-matching config
    new Event(details: [type: "orca:pipeline:complete"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'SUCCEEDED',
        notifications: [[type: "fake", when: "pipeline.failed"]]
      ]])                                                                                   || 0
    // notifications ON, failed pipeline and matching config
    new Event(details: [type: "orca:pipeline:failed"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'TERMINAL',
        notifications: [[type: "fake", when: "pipeline.failed"]]
      ]])                                                                                   || 1
    // notifications ON, failed pipeline and non-matching config
    new Event(details: [type: "orca:pipeline:failed"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'TERMINAL',
        notifications: [[type: "fake", when: "pipeline.complete"]]
      ]])                                                                                   || 0
    // notifications ON, cancelled pipeline (should skip notifications)
    // note: this case is a bit convoluted as the event type is still set to "failed" by orca for cancelled pipelines
    new Event(details: [type: "orca:pipeline:failed"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'CANCELED',
        notifications: [[type: "fake", when: "pipeline.failed"]]
      ]])                                                                                   || 0
    // notifications ON, another check for cancelled pipeline (should skip notifications)
    // note: this case is a bit convoluted as the event type is still set to "failed" by orca for cancelled pipelines
    new Event(details: [type: "orca:pipeline:failed"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'WHATEVER',
        canceled: true,
        notifications: [[type: "fake", when: "pipeline.failed"]]
      ]])                                                                                   || 0
    // notifications ON, another check for cancelled pipeline (should skip notifications)
    // note: this case is a bit convoluted as the event type is still set to "failed" by orca for cancelled pipelines
    new Event(details: [type: "orca:pipeline:failed"],
      content: [execution: [
        id: "1",
        name: "foo-pipeline",
        status: 'WHATEVER',
        canceled: true,
        notifications: [[type: "fake", when: "pipeline.failed"]]
      ]])                                                                                   || 0

    // TODO(lpollo): add cases for stages and tasks
  }
}
