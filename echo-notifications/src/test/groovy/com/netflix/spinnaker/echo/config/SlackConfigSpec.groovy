package com.netflix.spinnaker.echo.config

import spock.lang.Specification
import spock.lang.Subject

class SlackConfigSpec extends Specification {
  @Subject
  SlackConfig.SlackProperties configProperties

  def setup() {
    configProperties = new SlackConfig.SlackProperties()
  }

  def 'test slack incoming web hook is inferred correctly'() {
    given:

    when:
    configProperties.token = token

    then:
    configProperties.useIncomingWebhook == expectedUseIncomingWebHook
    configProperties.baseUrl == expectedEndpoint

    where:
    token                                          | expectedEndpoint                   | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://slack.com"                | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://hooks.slack.com"          | true
    "OLD/FASHION"                                  | "https://slack.com"                | false
    ""                                             | "https://slack.com"                | false
    null                                           | "https://slack.com"                | false
  }

  def 'test slack incoming web hook base url is defined'() {
    given:

    when:
    configProperties.token = token
    configProperties.baseUrl = baseUrl

    then:
    configProperties.useIncomingWebhook == expectedUseIncomingWebHook
    configProperties.baseUrl == expectedEndpoint

    where:
    token                                          | baseUrl               | expectedEndpoint                   | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://example.com" | "https://example.com"              | false
    "myOldFashionToken"                            | ""                    | "https://slack.com"                | false
    "myOldFashionToken"                            | null                  | "https://slack.com"                | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://example.com" | "https://example.com"              | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | ""                    | "https://hooks.slack.com"          | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | null                  | "https://hooks.slack.com"          | true
  }

  def 'test slack incoming web hook when forceUseIncomingWebhook'() {
    given:

    when:
    configProperties.token = token
    configProperties.forceUseIncomingWebhook = true
    configProperties.baseUrl = 'https://example.com'

    then:
    configProperties.useIncomingWebhook == expectedUseIncomingWebHook
    configProperties.baseUrl == expectedEndpoint

    where:
    token                                          | expectedEndpoint      | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://example.com" | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://example.com" | true
    "OLD/FASHION"                                  | "https://example.com" | true
    ""                                             | "https://example.com" | true
    null                                           | "https://example.com" | true
  }
}
