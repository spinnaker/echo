package com.netflix.spinnaker.echo.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import org.quartz.CronTrigger
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.triggers.CronTriggerImpl
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

class ScheduledActionsControllerSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()
  Scheduler scheduler = Mock(Scheduler)
  def mvc = MockMvcBuilders.standaloneSetup(new ScheduledActionsController(scheduler)).build()

  @Shared CronTrigger trigger1 = makeTrigger("1", "America/New_York", true)
  @Shared CronTrigger trigger2 = makeTrigger("1", "America/Los_Angeles", false)

  private CronTrigger makeTrigger(String id, String timezone, boolean rebake) {
    def trigger = new CronTriggerImpl(
      "key" + id, "user", "job","job",
      id + " 10 0/12 1/1 * ? *")

    trigger.jobDataMap.put("application", "app" + id)
    trigger.jobDataMap.put("id", "id" + id)
    trigger.jobDataMap.put("runAsUser", "runAsUser" + id)
    trigger.jobDataMap.put("timeZone", timezone)
    trigger.jobDataMap.put("triggerRebake", rebake)

    return trigger
  }

  void 'should get all triggers'() {
    given:
    def pipelineTriggerSet = new HashSet<TriggerKey>()
    def manualTriggerSet = new HashSet<TriggerKey>()

    pipelineTriggerSet.add(trigger1.key)
    manualTriggerSet.add(trigger2.key)

    scheduler.getTriggerKeys(_) >>> [pipelineTriggerSet, manualTriggerSet]
    scheduler.getTrigger(_) >>> [trigger1, trigger2]

    when:
    def result =
      mvc.perform(MockMvcRequestBuilders
        .get("/scheduledActions"))
        .andReturn()

    def responseBody = objectMapper.readValue(result.response.contentAsByteArray, Map)

    then:
    responseBody.pipeline.size() == 1
    responseBody.manuallyCreated.size() == 1

    responseBody.pipeline[0] == [
      id: trigger1.key.name,
      application: trigger1.jobDataMap.getString("application"),
      pipelineId: trigger1.jobDataMap.getString("id"),
      cronExpression: trigger1.cronExpression,
      runAsUser: trigger1.jobDataMap.getString("runAsUser"),
      timezone: trigger1.timeZone.getID(),
      forceRebake: trigger1.jobDataMap.getBoolean("triggerRebake")
    ]

    responseBody.manuallyCreated[0] == [
      id: trigger2.key.name,
      application: trigger2.jobDataMap.getString("application"),
      pipelineId: trigger2.jobDataMap.getString("id"),
      cronExpression: trigger2.cronExpression,
      runAsUser: trigger2.jobDataMap.getString("runAsUser"),
      timezone: trigger2.timeZone.getID(),
      forceRebake: trigger2.jobDataMap.getBoolean("triggerRebake")
    ]
  }

  void 'should fail creating a trigger with missing params'() {
    when:
    def result = mvc.perform(MockMvcRequestBuilders
      .post("/scheduledActions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andReturn()

    then:
    result.response.status == 400

    where:
    payload                                                                                    | _
    '{"id":"1", "application":"app", "pipelineId":"pipe"                                   }'  | _
    '{"id":"1", "application":"app",                      "cronExpression": "* * * * * * ?"}'  | _
    '{"id":"1",                      "pipelineId":"pipe", "cronExpression": "* * * * * * ?"}'  | _
    '{          "application":"app", "pipelineId":"pipe", "cronExpression": "* * * * * * ?"}'  | _
  }

  void 'should create a trigger with correct params'() {
    def payload = [
      id: "id1",
      application: "app",
      pipelineId: "pipe",
      cronExpression: "* 10 0/12 1/1 * ? *"
      ]

    when:
    def result =
      mvc.perform(MockMvcRequestBuilders
        .post("/scheduledActions")
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(payload)))
        .andReturn()

    def responseBody = objectMapper.readValue(result.response.contentAsByteArray, Map)

    then:
    1 * scheduler.scheduleJob(_ as Trigger) >> { t ->
      assert (t[0] instanceof CronTriggerImpl)
      CronTrigger trigger = t[0] as CronTrigger
      assert (trigger.key.name == payload.id)
      assert (trigger.cronExpression == payload.cronExpression)
      assert (trigger.timeZone == TimeZone.getDefault())
      assert (trigger.getJobDataMap().getString("id") == payload.pipelineId)
      assert (trigger.getJobDataMap().getString("application") == payload.application)
    }

    responseBody == payload + [
      forceRebake: false,
      runAsUser: null,
      timezone: TimeZone.getDefault().getID()]
  }

  void 'should delete trigger'() {
    scheduler.getTrigger(_) >> trigger1

    when:
    mvc.perform(MockMvcRequestBuilders
      .delete("/scheduledActions/key1"))
      .andReturn()

    then:
    1 * scheduler.getTrigger(_ as TriggerKey) >> { args ->
      assert (args[0] as TriggerKey).equals(trigger1.key)

      return trigger1
    }

    1 * scheduler.unscheduleJob(_ as TriggerKey) >> { args ->
      assert (args[0] as TriggerKey).equals(trigger1.key)

      return true
    }
  }
}
