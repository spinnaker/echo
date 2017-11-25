package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.slack.SlackMessage
import com.sun.jndi.toolkit.url.Uri
import groovy.json.JsonSlurper
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.client.Request
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedOutput
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.Collections.emptyList
import static retrofit.RestAdapter.*

class SlackConfigSpec extends Specification {
  @Subject SlackConfig slackConfig = new SlackConfig()

  def 'test slack incoming web hook is inferred correctly'() {
    given:

    when:
    def useIncomingHook = slackConfig.useIncomingWebHook(token)
    def endpoint = slackConfig.slackEndpoint(useIncomingHook)

    then:
    useIncomingHook == expectedUseIncomingWebHook
    endpoint.url == expectedEndpoint

    where:
    token                                          | expectedEndpoint          | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://slack.com"       | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://hooks.slack.com" | true
    "OLD/FASHION"                                  | "https://slack.com"       | false
    ""                                             | "https://slack.com"       | false
    null                                           | "https://slack.com"       | false
  }
}
