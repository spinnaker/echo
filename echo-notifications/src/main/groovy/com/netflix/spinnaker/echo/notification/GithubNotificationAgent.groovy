/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.github.GithubService
import com.netflix.spinnaker.echo.github.GithubStatus
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

import static net.logstash.logback.argument.StructuredArguments.kv


@Slf4j
@ConditionalOnProperty('github.enabled')
@Service
class GithubNotificationAgent extends AbstractEventNotificationAgent {

  @Autowired
  GithubService githubService

  @Value('${github.token}')
  String token

  @Override
  void sendNotifications(Map preference, String application, Event event, Map config, String status) {
    Map<String, ?> execution = (Map<String, ?>) event.getContent().get("execution")

    String repo = execution.trigger?.buildInfo?.name
    if (repo == null) {
      return
    }

    List scm = execution.trigger?.buildInfo?.scm
    if (scm == null) {
      return
    }

    String sha = scm[0]?.sha1
    if (sha == null) {
      return
    }

    String state = 'error'
    if (status == 'failed') {
      state = "failure"
    }

    if (status == 'starting') {
      state = "pending"
    }

    if (status == 'complete') {
      state = "success"
    }

    String pipeline = execution.get('name')

    String description, context, targetUrl

    if (config.type == 'stage') {
      String stageName = event.content.name ?: event.content?.context?.stageDetails?.name
      description = """Stage '${stageName}' in pipeline '${pipeline}' is ${status}"""
      context = "Stage"

      List stages = execution.get('stages')
      LinkedHashMap stage = stages.find { it.name == stageName }
      int stageId = stages.indexOf(stage)

      targetUrl = """${spinnakerUrl}/#/applications/${application}/${event.content?.execution?.id}?pipeline=${
        pipeline
      }&stage=${stageId}"""
    } else if (config.type == 'pipeline') {
      description = """Pipeline '${pipeline}' is ${status}"""
      context = "Pipeline"

      targetUrl = """${spinnakerUrl}/#/applications/${application}/${event.content?.execution?.id}"""
    } else {
      return
    }

    log.info('Sending Github status check: {} {} {} {} {} {}',
      kv('pipeline', pipeline),
      kv('repo', repo),
      kv('sha', sha),
      kv('targetUrl', targetUrl),
      kv('state', state),
      kv('description', description))

    try {

      githubService.updateCheck("""token ${token}""", repo, sha, new GithubStatus(
        state: state,
        target_url: targetUrl,
        description: description,
        context: context))

    } catch (Exception e) {
      log.error('failed to send github status ', e)
    }
  }

  @Override
  String getNotificationType() {
    'github'
  }

}

