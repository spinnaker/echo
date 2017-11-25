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


package com.netflix.spinnaker.echo.slack

import groovy.json.JsonBuilder
import groovy.transform.Canonical

@Canonical
class SlackMessage {

  String text
  String color
  String fallback
  String title
  String footer = "Spinnaker"
  String footer_icon = "https://avatars0.githubusercontent.com/u/7634182?s=200&v=4" // From https://github.com/spinnaker
  long ts = System.currentTimeMillis() / 1000

  public SlackMessage(String title, String text, String color = '#cccccc') {
    this.title = title
    this.text = this.fallback = text
    this.color = color
  }

  /**
   * To display a message with a colored vertical bar on the left, Slack expects the message to be in an "attachments"
   * field as a JSON string of an array of objects, e.g.
   *   [{"fallback":"plain-text summary", "text":"the message to send", "color":"#hexcolor"}]
   * @return a stringified version of the JSON array containing the attachment
   */
  String buildMessage() {
    new JsonBuilder([
      [
        fallback: text,
        text: text,
        color: color,
        mrkdwn_in: ["text"]
      ]
    ]).toString()
  }
}
