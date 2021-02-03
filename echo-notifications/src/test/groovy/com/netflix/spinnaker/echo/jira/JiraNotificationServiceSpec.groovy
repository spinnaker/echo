package com.netflix.spinnaker.echo.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification


class JiraNotificationServiceSpec extends Specification {

  def jiraService = Mock(JiraService)
  def retrySupport = new RetrySupport()
  def objectMapper = Mock(ObjectMapper)
  JiraNotificationService service = new JiraNotificationService(jiraService, retrySupport, objectMapper)

  void 'Handles Jira transition'() {
    given:
    def notification = new Notification(
      notificationType: "JIRA",
      source: new Notification.Source(user: "foo@example.com"),
      additionalContext: [
        jiraIssue: "EXMPL-0000",
        transitionContext: [
          transition: [
            name: "Done"
          ]
        ]
      ]
    )

    when:
    service.handle(notification)

    then:
    1 * jiraService.getIssueTransitions(_) >> new JiraService.IssueTransitions(transitions: [new JiraService.IssueTransitions.Transition(name: "Done", id: "4")])
    1 * jiraService.transitionIssue(_, _)
  }
}
