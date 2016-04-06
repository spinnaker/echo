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

import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import com.netflix.spinnaker.echo.model.trigger.WebhookEvent

@RestController
@Slf4j
class WebhooksController {

  @Autowired
  EventPropagator propagator

  @RequestMapping(value = '/webhooks/{type}/{source}', method = RequestMethod.POST)
  void forwardEvent(@PathVariable String type, @PathVariable String source, @RequestBody Map postedEvent) {
    Event event = new Event()
    boolean sendEvent = true
    event.details = new Metadata()
    event.details.source = source
    event.details.type = type
    event.content = postedEvent

    if (type == 'git') {
      if (source == 'stash') {
        event.content.hash = postedEvent.refChanges?.first().toHash
        event.content.branch = postedEvent.refChanges?.first().refId.replace('refs/heads/', '')
        event.content.repoProject = postedEvent.repository.project.key
        event.content.slug = postedEvent.repository.slug
        if (event.content.hash.toString().startsWith('000000000')) {
          sendEvent = false
        }
      }
      if (source == 'github') {
        event.content.hash = postedEvent.after
        event.content.branch = postedEvent.ref.replace('refs/heads/', '')
        event.content.repoProject = postedEvent.repository.owner.name
        event.content.slug = postedEvent.repository.name
      }
    }

    log.info("Webhook ${type}:${source}:${event.content}")

    if (sendEvent) {
      propagator.processEvent(event)
    }
  }

  @RequestMapping(value = '/webhooks/trigger/{category}/{source}', method = RequestMethod.POST)
  void forwardWebHookEvent(@PathVariable String category, @PathVariable String source, @RequestBody Map postedEvent) {
    Event event = new Event()
    boolean sendEvent = true
    event.details = new Metadata()
    event.details.type = WebhookEvent.TYPE
    event.details.category = category
    event.details.source = source
    event.content = postedEvent

    if (category == 'git') {
      if (source == 'stash') {
        event.content.hash = postedEvent.refChanges?.first().toHash
        event.content.branch = postedEvent.refChanges?.first().refId.replace('refs/heads/', '')
        event.content.repoProject = postedEvent.repository.project.key
        event.content.slug = postedEvent.repository.slug
        if (event.content.hash.toString().startsWith('000000000')) {
          sendEvent = false
        }
      }
      if (source == 'github') {
        event.content.hash = postedEvent.after
        event.content.branch = postedEvent.ref.replace('refs/heads/', '')
        event.content.repoProject = postedEvent.repository.owner.name
        event.content.slug = postedEvent.repository.name
      }
    }

    log.info("Webhook ${WebhookEvent.TYPE}:${category}:${source}::${event.content}")

    if (sendEvent) {
      propagator.processEvent(event)
    }
  }

}
