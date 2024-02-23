/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.controllers


import com.netflix.spinnaker.echo.artifacts.ArtifactExtractor
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.scm.BitbucketWebhookEventHandler
import com.netflix.spinnaker.echo.scm.GithubWebhookEventHandler
import com.netflix.spinnaker.echo.scm.GitlabWebhookEventHandler
import com.netflix.spinnaker.echo.scm.ScmWebhookHandler
import com.netflix.spinnaker.echo.scm.StashWebhookEventHandler
import com.netflix.spinnaker.echo.scm.bitbucket.server.BitbucketServerEventHandler
import org.springframework.http.HttpHeaders
import spock.lang.Specification
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException


import java.nio.charset.StandardCharsets

class WebhooksControllerSpec extends Specification {

  ScmWebhookHandler scmWebhookHandler = new ScmWebhookHandler(
    [
      new BitbucketWebhookEventHandler(new BitbucketServerEventHandler()),
      new GitlabWebhookEventHandler(),
      new GithubWebhookEventHandler(),
      new StashWebhookEventHandler()]
  )

  void 'emits a transformed event for every webhook event'() {

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    controller.forwardEvent(
      'docker', 'ecr', '{"name": "something"}', new HttpHeaders()
    )

    then:
    1 * controller.propagator.processEvent(
      {
        it.details.type == 'docker' &&
          it.details.source == 'ecr' &&
          it.content.name == 'something'
      }
    )

  }

  void 'handles initial github ping'() {
    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    controller.forwardEvent(
      'git',
      'github',
      '{"hook_id": 1337, "repository": {"full_name": "org/repo"}}',
      new HttpHeaders())

    then:
    0 * controller.propagator.processEvent(_)
  }

  void 'handles Bitbucket Server ping'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "repo:refs_changed")

    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '',
      headers)

    then:
    0 * controller.propagator.processEvent(_)

    response.eventProcessed == false
  }

  void 'handles Bitbucket Server Push Webhook'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "repo:refs_changed")

    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '''{
          "eventKey":"repo:refs_changed",
          "date":"2017-09-19T09:45:32+1000",
          "actor":{
            "name":"admin",
            "emailAddress":"admin@example.com",
            "id":1,
            "displayName":"Administrator",
            "active":true,
            "slug":"admin",
            "type":"NORMAL"
          },
          "repository":{
            "slug":"repository",
            "id":84,
            "name":"repository",
            "scmId":"git",
            "state":"AVAILABLE",
            "statusMessage":"Available",
            "forkable":true,
            "project":{
              "key":"PROJ",
              "id":84,
              "name":"project",
              "public":false,
              "type":"NORMAL"
            },
            "public":false
          },
          "changes":[
            {
              "ref":{
                "id":"refs/heads/master",
                "displayId":"master",
                "type":"BRANCH"
              },
              "refId":"refs/heads/master",
              "fromHash":"ecddabb624f6f5ba43816f5926e580a5f680a932",
              "toHash":"178864a7d521b6f5e720b386b2c2b0ef8563e0dc",
              "type":"UPDATE"
            }
          ]
        }''', headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
      event.content.branch == 'master'
    }

    response.eventProcessed == true
    response.eventId == event.eventId
    event.content.branch == "master"
    event.content.repoProject == "PROJ"
    event.content.slug == "repository"
    event.content.hash == "178864a7d521b6f5e720b386b2c2b0ef8563e0dc"
    event.content.action == "repo:refs_changed"

  }

  void 'handles Bitbucket Server Merge Webhook'() {
    def event
    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "pr:merged")


    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '''{
          "eventKey":"pr:merged",
          "date":"2017-09-19T10:39:36+1000",
          "actor":{
            "name":"user",
            "emailAddress":"user@example.com",
            "id":2,
            "displayName":"User",
            "active":true,
            "slug":"user",
            "type":"NORMAL"
          },
          "pullRequest":{
            "id":9,
            "version":2,
            "title":"file edited online with Bitbucket",
            "state":"MERGED",
            "open":false,
            "closed":true,
            "createdDate":1505781560908,
            "updatedDate":1505781576361,
            "closedDate":1505781576361,
            "fromRef":{
              "id":"refs/heads/admin/file-1505781548644",
              "displayId":"admin/file-1505781548644",
              "latestCommit":"45f9690c928915a5e1c4366d5ee1985eea03f05d",
              "repository":{
                "slug":"repository",
                "id":84,
                "name":"repository",
                "scmId":"git",
                "state":"AVAILABLE",
                "statusMessage":"Available",
                "forkable":true,
                "project":{
                  "key":"PROJ",
                  "id":84,
                  "name":"project",
                  "public":false,
                  "type":"NORMAL"
                },
                "public":false
              }
            },
            "toRef":{
              "id":"refs/heads/master",
              "displayId":"master",
              "latestCommit":"8d2ad38c918fa6943859fca2176c89ea98b92a21",
              "repository":{
                "slug":"repository",
                "id":84,
                "name":"repository",
                "scmId":"git",
                "state":"AVAILABLE",
                "statusMessage":"Available",
                "forkable":true,
                "project":{
                  "key":"PROJ",
                  "id":84,
                  "name":"project",
                  "public":false,
                  "type":"NORMAL"
                },
                "public":false
              }
            },
            "locked":false,
            "author":{
              "user":{
                "name":"admin",
                "emailAddress":"admin@example.com",
                "id":1,
                "displayName":"Administrator",
                "active":true,
                "slug":"admin",
                "type":"NORMAL"
              },
              "role":"AUTHOR",
              "approved":false,
              "status":"UNAPPROVED"
            },
            "reviewers":[
            ],
            "participants":[
              {
                "user":{
                  "name":"user",
                  "emailAddress":"user@example.com",
                  "id":2,
                  "displayName":"User",
                  "active":true,
                  "slug":"user",
                  "type":"NORMAL"
                },
                "role":"PARTICIPANT",
                "approved":false,
                "status":"UNAPPROVED"
              }
            ],
            "properties":{
              "mergeCommit":{
                "displayId":"7e48f426f0a",
                "id":"7e48f426f0a6e47c5b5e862c31be6ca965f82c9c"
              }
            }
          }
        }''', headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
      event.content.branch == 'master'
    }

    response.eventProcessed == true
    response.eventId == event.eventId
    event.content.branch == "master"
    event.content.hash == "7e48f426f0a6e47c5b5e862c31be6ca965f82c9c"
    event.content.repoProject == "PROJ"
    event.content.slug == "repository"
    event.content.action == "pr:merged"
  }

  void 'returns success status with eventId'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'webhook',
      'test',
      '{}',
      new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
      event.content.repoProject == 'PROJ'
    }

    response.eventProcessed == true
    response.eventId == event.eventId
  }

  void 'no source returns success status with eventId'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'webhook',
      [:],
      new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
      event.content.repoProject == 'PROJ'
    }

    response.eventProcessed == true
    response.eventId == event.eventId
  }

  void 'handles Github Webhook Push Event'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "github",
      """{
          "ref": "refs/heads/simple-tag",
          "before": "a10867b14bb761a232cd80139fbd4c0d33264240",
          "after": "0000000000000000000000000000000000000000",
          "repository": {
            "name": "Hello-World",
            "owner": {
              "name": "Codertocat"
            }
          },
          "commits": [
            {
              "id": "0000000000000000000000000000000000000000"
            }
          ]
        }
        """, new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "0000000000000000000000000000000000000000"
    event.content.repoProject == "Codertocat"
    event.content.slug == "Hello-World"
    event.content.branch == "simple-tag"
    event.content.action == "push:push"
  }

  void 'handles Github PR Webhook Event'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "github",
      """{
          "action": "opened",
          "pull_request": {
            "number": 42,
            "head": {
              "ref": "simple-tag",
              "sha": "0000000000000000000000000000000000000000"
            },
            "title": "Very nice Pull Request",
            "draft": false,
            "state": "open"
          },
          "repository": {
            "name": "Hello-World",
            "owner": {
              "login": "Codertocat"
            }
          }
        }
        """, new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "0000000000000000000000000000000000000000"
    event.content.repoProject == "Codertocat"
    event.content.slug == "Hello-World"
    event.content.branch == "simple-tag"
    event.content.action == "pull_request:opened"
    event.content.number == "42"
    event.content.title == "Very nice Pull Request"
    event.content.state == "open"
    event.content.draft == "false"
  }

  void 'gracefully handle Github PR Webhook Event With no Action'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "github",
      """{
          "action": null,
          "pull_request": {
            "number": 42,
            "head": {
              "ref": "simple-tag",
              "sha": "0000000000000000000000000000000000000000"
            },
            "title": "Very nice Pull Request",
            "draft": false,
            "state": "open"
          },
          "repository": {
            "name": "Hello-World",
            "owner": {
              "login": "Codertocat"
            }
          }
        }
        """, new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "0000000000000000000000000000000000000000"
    event.content.repoProject == "Codertocat"
    event.content.slug == "Hello-World"
    event.content.branch == "simple-tag"
    event.content.action == "pull_request:"
    event.content.number == "42"
    event.content.title == "Very nice Pull Request"
    event.content.state == "open"
    event.content.draft == "false"
  }

  void 'handles non-push Github Webhook Event gracefully'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "github",
      """{
          "before": "a10867b14bb761a232cd80139fbd4c0d33264240",
          "after": "0000000000000000000000000000000000000000",
          "repository": {
            "name": "Hello-World",
            "owner": {
              "name": "Codertocat"
            }
          }
        }
        """, new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "0000000000000000000000000000000000000000"
    event.content.repoProject == "Codertocat"
    event.content.slug == "Hello-World"
    event.content.branch == ""
    event.content.action == "push:push"
  }

  void "handles Gitlab Webhook Event"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "gitlab",
      """{
          "object_kind": "push",
          "after": "da1560886d4f094c3e6c9ef40349f7d38b5d27d7",
          "ref": "refs/heads/master",
          "project":{
            "name":"Diaspora",
            "namespace":"Mike"
          }
        }
        """,new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "da1560886d4f094c3e6c9ef40349f7d38b5d27d7"
    event.content.repoProject == "Mike"
    event.content.slug == "Diaspora"
    event.content.branch == "master"
    event.content.action == "push"
  }

  void "handles Stash Webhook Event"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      "git",
      "stash",
      """{
          "refChanges": [
            {"toHash": "firstHash", "refId": "refs/heads/master"},
            {"toHash": "secondHash", "refId": "refs/heads/master"}
          ],
          "repository": {
            "slug": "echo",
            "project": {"key": "spinnaker"}
          }
        }
        """,new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "firstHash"
    event.content.repoProject == "spinnaker"
    event.content.slug == "echo"
    event.content.branch == "master"
    event.content.action == ""
  }

  void "handles Bitbucket Cloud Webhook PR Event"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "pullrequest:fulfilled")

    when:
    def response = controller.forwardEvent(
      "git",
      "bitbucket",
      """{
          "repository": {
            "full_name": "echo",
            "owner": {"username": "spinnaker"}
          },
          "pullrequest": {
            "merge_commit": {
              "hash": "firstHash"
             },
             "destination": {
              "branch": {"name": "master"}
             }
          }
        }
        """,headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "firstHash"
    event.content.repoProject == "spinnaker"
    event.content.slug == "echo"
    event.content.branch == "master"
    event.content.action == "pullrequest:fulfilled"
  }

  void "handles Bitbucket Cloud Webhook PR Event with Project key"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "pullrequest:fulfilled")

    when:
    def response = controller.forwardEvent(
      "git",
      "bitbucket",
      """{
          "repository": {
            "full_name": "echo",
            "owner": {
              "display_name": "spinnaker"
            },
            "project": {
                "key": "ECH"
            }
          },
          "pullrequest": {
            "merge_commit": {
              "hash": "firstHash"
             },
             "destination": {
              "branch": {"name": "master"}
             }
          }
        }
        """,headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "firstHash"
    event.content.repoProject == "ECH"
    event.content.slug == "echo"
    event.content.branch == "master"
    event.content.action == "pullrequest:fulfilled"
  }

  void "handles Bitbucket Cloud Webhook Push Event"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "repo:push")

    when:
    def response = controller.forwardEvent(
      "git",
      "bitbucket",
      """{
          "repository": {
            "full_name": "echo",
            "owner": {"username": "spinnaker"}
          },
          "push": {
            "changes": [
              {
                "new": {
                  "type": "branch",
                  "name": "master"
                },
                "commits": [
                  {
                   "hash": "firstHash"
                  }
                ]
              }
            ]
          }
        }
        """,headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "firstHash"
    event.content.repoProject == "spinnaker"
    event.content.slug == "echo"
    event.content.branch == "master"
    event.content.action == "repo:push"
  }

  void "handles Bitbucket Cloud Webhook Push Event with Project key"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Event-Key", "repo:push")

    when:
    def response = controller.forwardEvent(
      "git",
      "bitbucket",
      """{
          "repository": {
            "full_name": "echo",
            "owner": {"display_name": "spinnaker"},
            "project": {
                "key": "ECH"
            }
          },
          "push": {
            "changes": [
              {
                "new": {
                  "type": "branch",
                  "name": "master"
                },
                "commits": [
                  {
                   "hash": "firstHash"
                  }
                ]
              }
            ]
          }
        }
        """,headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.hash == "firstHash"
    event.content.repoProject == "ECH"
    event.content.slug == "echo"
    event.content.branch == "master"
    event.content.action == "repo:push"
  }

  void "handles CDEvents Webhook with Valid CDEvent Data"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)

    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.details.type == "cdevents"
    event.details.source == "artifactPublished"
    event.rawContent == cdEventData
  }

  void "handles valid CDEvents Webhook Event with Attribute Constraints in request Headers"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)

    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.details.requestHeaders.get("Ce-Type")[0] == "dev.cdevents.artifact.published"
    event.details.requestHeaders.get("Ce-Id")[0] == "1234"
    event.details.requestHeaders.get("Ce-Specversion")[0] == "1.0"
    event.details.requestHeaders.get("Ce-Data")[0] == cdEventData

  }

  void "handles valid CDEvents Webhook Event with Payload Constraints in customData"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)

    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {\n" +
      "    \"mykey\": \"myvalue\",\n" +
      "    \"bing\": \"boooop\"\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.payload.get("customData").get("mykey") == "myvalue"
    event.payload.get("customData").get("bing") == "boooop"
  }

  void "handles valid CDEvents Webhook Event with Parameter in customData"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)

    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {\n" +
      "    \"parameters\": {\n" +
      "      \"stack\": \"prod\"\n" +
      "    }\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();

    when:
    def response = controller.forwardEvent("artifactPublished",cdevent, headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.parameters.get("stack") == "prod"

  }

  void "handles valid CDEvents Webhook Event with Artifact Constraints in customData"() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)

    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {\n" +
      "    \"artifacts\": [\n" +
      "      {\n" +
      "        \"type\": \"custom/object\",\n" +
      "        \"name\": \"image-cdevents\",\n" +
      "        \"version\": \"1.0.1\",\n" +
      "        \"reference\": \"custom/object/artifact\"\n" +
      "      }\n" +
      "    ]\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();


    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    event.content.artifacts[0].get("name") == "image-cdevents"
    event.content.artifacts[0].get("version") == "1.0.1"
    event.content.artifacts[0].get("reference") == "custom/object/artifact"
    event.content.artifacts[0].get("type") == "custom/object"
  }

  void "handles invalid CDEvents Webhook Event with out customData"() {
    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders()
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();
    Map dataMap = new ObjectMapper().readValue(cdEventData, Map) ?: [:]

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)
    then:
    def exception = thrown(InvalidRequestException)
    exception.message == "Invalid CDEvent data posted with the CloudEvent RequestBody - $dataMap"

  }

  void "handles invalid CDEvents Webhook Event with no CDEvents context"() {
    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    String cdEventData = "{\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {},\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders()
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();
    Map dataMap = new ObjectMapper().readValue(cdEventData, Map) ?: [:]

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)
    then:
    def exception = thrown(InvalidRequestException)
    exception.message == "Invalid CDEvent data posted with the CloudEvent RequestBody - $dataMap"

  }

  void "handles invalid CDEvents Webhook Event with no CDEvents subject"() {
    given:
    WebhooksController controller = new WebhooksController(mapper: EchoObjectMapper.getInstance(), scmWebhookHandler: scmWebhookHandler)
    controller.propagator = Mock(EventPropagator)
    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"customData\": {},\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    HttpHeaders headers = new HttpHeaders()
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.published")
    headers.add("Ce-Data", cdEventData)

    CloudEvent cdevent = CloudEventBuilder.v1()
      .withId("12345")
      .withType("dev.cdevents.artifact.published")
      .withSource(URI.create("https://cdevents.dev"))
      .build();
    Map dataMap = new ObjectMapper().readValue(cdEventData, Map) ?: [:]

    when:
    controller.forwardEvent("artifactPublished",cdevent, headers)
    then:
    def exception = thrown(InvalidRequestException)
    exception.message == "Invalid CDEvent data posted with the CloudEvent RequestBody - $dataMap"
  }
}
