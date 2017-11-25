package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.config.SlackConfig
import groovy.json.JsonSlurper
import retrofit.client.Client
import retrofit.client.Request
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedOutput
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.Collections.emptyList
import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.RestAdapter.LogLevel

class SlackServiceSpec extends Specification {
  @Subject mockHttpClient = Mock(Client)
  @Subject slackConfig = new SlackConfig()

  def 'test sending Slack notification using incoming web hook'() {

    given: "a SlackService configured to send using a mocked HTTP client"
    def actualUrl = new BlockingVariable<String>()
    def actualPayload = new BlockingVariable<String>()

    // intercepting the HTTP call
    mockHttpClient.execute(*_) >> { Request request ->
      actualUrl.set(request.url)
      actualPayload.set(getString(request.body))
      mockResponse()
    }

    def slackService = slackConfig.slackService(useIncomingHook, endpoint, mockHttpClient, LogLevel.FULL)

    when: "sending a notification"
    slackService.sendMessage(token, new SlackMessage("Title", "the text"), "#testing", true)
    def responseJson = new JsonSlurper().parseText(actualPayload.get())

    then: "the HTTP URL and payload intercepted are the ones expected"
    actualUrl.get() == expectedUrl
    responseJson.attachments[0]["title"] == "Title"
    responseJson.attachments[0]["text"] == "the text"
    responseJson.attachments[0]["footer"] == "Spinnaker"
    responseJson.channel == "#testing"

    where:
    token            | expectedUrl
    "NEW/TYPE/TOKEN" | "https://hooks.slack.com/services/NEW/TYPE/TOKEN"

    useIncomingHook = true
    endpoint = newFixedEndpoint(SlackConfig.SLACK_INCOMING_WEBHOOK)
  }

  static Response mockResponse() {
    new Response("url", 200, "nothing", emptyList(), new TypedByteArray("application/json", "response".bytes))
  }

  static String getString(TypedOutput typedOutput) {
    OutputStream os = new ByteArrayOutputStream()
    typedOutput.writeTo(os)
    new String(os.toByteArray(),"UTF-8")
  }
}
