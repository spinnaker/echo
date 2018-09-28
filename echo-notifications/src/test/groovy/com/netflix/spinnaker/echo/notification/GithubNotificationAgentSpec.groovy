/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.github.GithubService
import com.netflix.spinnaker.echo.github.GithubStatus
import com.netflix.spinnaker.echo.model.Event
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

class GithubNotificationAgentSpec extends Specification {

  def github = Mock(GithubService)
  @Subject
  def agent = new GithubNotificationAgent(githubService: github)


  @Unroll
  def "sets correct status check for #status status in pipeline events"() {
    given:
    agent.spinnakerUrl = "http://spinnaker.io"
    def actualMessage = new BlockingVariable<GithubStatus>()
    github.updateCheck(*_) >> { token, repo, sha, status ->
      actualMessage.set(status)
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    actualMessage.get().getDescription() ==~ expectedDescription
    actualMessage.get().getTarget_url() == "http://spinnaker.io/#/applications/whatever/executions/details/1?pipeline=foo-pipeline"
    actualMessage.get().getContext() ==~ "Pipeline"

    where:
    status     || expectedDescription
    "complete" || "Pipeline 'foo-pipeline' is complete"
    "starting" || "Pipeline 'foo-pipeline' is starting"
    "failed"   || "Pipeline 'foo-pipeline' is failed"

    application = "whatever"
    event = new Event(
      content: [
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ]
        ]
      ]
    )
    type = "pipeline"
  }

  @Unroll
  def "sets correct status check for #status status in stage events"() {
    given:
    agent.spinnakerUrl = "http://spinnaker.io"
    def actualMessage = new BlockingVariable<GithubStatus>()
    github.updateCheck(*_) >> { token, repo, sha, status ->
      actualMessage.set(status)
    }

    when:
    agent.sendNotifications(null, application, event, [type: type], status)

    then:
    actualMessage.get().getDescription() == expectedDescription
    actualMessage.get().getTarget_url() == "http://spinnaker.io/#/applications/whatever/executions/details/1?pipeline=foo-pipeline&stage=1"
    actualMessage.get().getContext() == "Stage"

    where:
    status     || expectedDescription
    "complete" || "Stage 'second stage' in pipeline 'foo-pipeline' is complete"
    "starting" || "Stage 'second stage' in pipeline 'foo-pipeline' is starting"
    "failed"   || "Stage 'second stage' in pipeline 'foo-pipeline' is failed"

    application = "whatever"
    event = new Event(
      content: [
        name     : "second stage",
        execution: [
          id     : "1",
          name   : "foo-pipeline",
          trigger: [
            buildInfo: [
              name: "some-org/some-repo",
              scm : [
                [
                  branch: "master",
                  name  : "master",
                  sha1  : "asdf",
                ]
              ]
            ]
          ],
          stages : [
            [
              name: "first stage"
            ],
            [
              name: "second stage"
            ],
          ]
        ]
      ]
    )
    type = "stage"
  }
}
