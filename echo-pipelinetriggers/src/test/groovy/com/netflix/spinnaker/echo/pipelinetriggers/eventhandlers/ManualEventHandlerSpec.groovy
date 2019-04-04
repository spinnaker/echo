/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.artifacts.ArtifactInfoService
import com.netflix.spinnaker.echo.build.BuildInfoService
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Subject

class ManualEventHandlerSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def buildInfoService = Mock(BuildInfoService)
  def artifactInfoService = Mock(ArtifactInfoService)

  private static final Logger log = LoggerFactory.getLogger(ManualEventHandler.class)

  Artifact artifact = new Artifact(
    "deb",
    false,
    "my-package",
    "v1.1.1",
    "https://artifactory/my-package/",
    "https://artifactory/my-package/",
    [:],
    "account",
    "provenance",
    "123456"
  )

  @Subject
  def eventHandler = new ManualEventHandler(
    objectMapper,
    Optional.of(buildInfoService),
    Optional.of(artifactInfoService)
  )

  def "should replace artifact with full version if it exists"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v1.1.1",
      location: "artifactory"
    ]

    Trigger trigger = Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build()
    Pipeline inputPipeline = createPipelineWith(trigger)
    Trigger manualTrigger = Trigger.builder().type("manual").artifacts([triggerArtifact]).build()

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1") >> artifact
    def resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger)

    then:
    resolvedPipeline.receivedArtifacts.size() == 1
    resolvedPipeline.receivedArtifacts.first().name == "my-package"
  }

  def "should resolve artifact if it exists"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v1.1.1",
      location: "artifactory"
    ]
    List<Map<String, Object>> triggerArtifacts = [triggerArtifact]

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v1.1.1") >> artifact
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts)

    then:
    resolvedArtifacts.size() == 1
    resolvedArtifacts.first().name == "my-package"
  }

  def "should not resolve artifact if it doesn't exist"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v2.2.2",
      location: "artifactory"
    ]
    List<Map<String, Object>> triggerArtifacts = [triggerArtifact]

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> { throw new NotFoundException() }
    List<Artifact> resolvedArtifacts = eventHandler.resolveArtifacts(triggerArtifacts)

    then:
    resolvedArtifacts.size() == 0
  }

  def "should remove artifact if it doesn't exist"() {
    given:
    Map<String, Object> triggerArtifact = [
      name: "my-package",
      version: "v2.2.2",
      location: "artifactory"
    ]
    Trigger trigger = Trigger.builder().enabled(true).type("artifact").artifactName("my-package").build()
    Pipeline inputPipeline = createPipelineWith(trigger)
    Trigger manualTrigger = Trigger.builder().type("manual").artifacts([triggerArtifact]).build()

    when:
    artifactInfoService.getArtifactByVersion("artifactory", "my-package", "v2.2.2") >> { throw new NotFoundException() }
    def resolvedPipeline = eventHandler.buildTrigger(inputPipeline, manualTrigger)

    then:
    resolvedPipeline.receivedArtifacts.size() == 0
  }

}


