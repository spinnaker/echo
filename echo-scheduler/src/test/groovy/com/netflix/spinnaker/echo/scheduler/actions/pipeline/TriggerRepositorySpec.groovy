package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.TriggerRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TriggerRepositorySpec extends Specification {
  private static final String TRIGGER_ID = '74f13df7-e642-4f8b-a5f2-0d5319aa0bd1'
  private static final String ACTION_INSTANCE_ID = "2d05822d-0275-454b-9616-361bf3b557ca:com.netflix.scheduledactions.ActionInstance:${TRIGGER_ID}"

  @Shared Trigger triggerA = Trigger.builder().id('123-456').build()
  @Shared Trigger triggerB = Trigger.builder().id('456-789').build()
  @Shared Trigger triggerC = Trigger.builder().id(null).build() // to test the fallback mechanism
  @Shared Trigger triggerD = Trigger.builder().id('123-789').build() // not assigned to any pipeline

  @Shared Pipeline pipelineA = Pipeline.builder().application('app').name('pipeA').id('idPipeA').triggers([triggerA]).build()
  @Shared Pipeline pipelineB = Pipeline.builder().application('app').name('pipeB').id('idPipeB').triggers([triggerB, triggerC]).build()
  @Shared Pipeline pipelineC = Pipeline.builder().application('app').name('pipeC').build()

  @Shared TriggerRepository repo = new TriggerRepository([pipelineA, pipelineB, pipelineC])

  @Unroll
  def 'looking up id #id in repo should return trigger #trigger'() {
    when:
    Trigger result = repo.getTrigger(id)

    then:
    result == trigger
    result?.parent == pipeline

    where:
    id                      || trigger  | pipeline
    triggerA.idWithFallback || triggerA | pipelineA
    triggerB.idWithFallback || triggerB | pipelineB
    triggerC.idWithFallback || triggerC | pipelineB
    triggerC.id             || null     | null      // this is why we have idWithFallback
    triggerD.idWithFallback || null     | null      // not in our repo
  }


  @Unroll
  def 'we can remove triggers by id'() {
    given:
    TriggerRepository repo = new TriggerRepository([pipelineA, pipelineB, pipelineC])

    when: 'we remove using a trigger id directly'
    Trigger removed = repo.remove(triggerA.idWithFallback)

    then: 'it is effectively removed'
    removed == triggerA
    repo.triggers().size() == 2
    !repo.triggers().contains(triggerA)

    when: 'we remove using a compound id'
    removed = repo.remove("${pipelineB.id}:com.netflix.scheduledactions.ActionInstance:${triggerB.idWithFallback}")

    then: 'it is also effectively removed'
    removed == triggerB
    repo.triggers().size() == 1
    repo.triggers().contains(triggerC)

    when: 'we remove a thing that is not there'
    removed = repo.remove(triggerA.idWithFallback)

    then: 'everything is ok'
    removed == null
    repo.triggers().size() == 1
  }


  @Unroll
  def 'we can extract a trigger id from an action instance id'() {
    when:
    String result = TriggerRepository.extractTriggerId(inputId)

    then:
    result == triggerId

    where:
    inputId             || triggerId
    ACTION_INSTANCE_ID  || '74f13df7-e642-4f8b-a5f2-0d5319aa0bd1'
    TRIGGER_ID          || TRIGGER_ID // no-op if we pass something else
    ":${TRIGGER_ID}"    || TRIGGER_ID // fishing for off-by-one errors
    "${TRIGGER_ID}:"    || '' // we should not freak out if we get something plain unexpected
  }


  def 'we generate fallback ids based on cron expressions and parent pipelines'() {
    when: 'they have an explicit id'
    Trigger every5minNoParent = Trigger.builder().id('idA').cronExpression('*/5 * * * *').parent(null).build()

    then: 'the fallback id is the explicit id'
    every5minNoParent.idWithFallback == every5minNoParent.id
    every5minNoParent.idWithFallback == 'idA'


    when: 'two triggers have no id, no parent and the same cron expression'
    Trigger nullIdEvery5minNoParentA = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(null).build()
    Trigger nullIdEvery5minNoParentB = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(null).build()

    then: 'they get the same fallback id'
    nullIdEvery5minNoParentA.idWithFallback == nullIdEvery5minNoParentB.idWithFallback


    when: 'two triggers have different parents'
    Trigger nullIdEvery5minParentA = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(pipelineA).build()
    Trigger nullIdEvery5minParentB = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(pipelineB).build()

    then: 'they get different fallback ids'
    nullIdEvery5minParentA.idWithFallback != nullIdEvery5minParentB.idWithFallback


    when: 'two triggers have a different cron expression'
    Trigger nullIdEvery30minParentA = Trigger.builder().id(null).cronExpression('*/30 * * * *').parent(pipelineA).build()

    then: 'they get different fallback ids'
    nullIdEvery5minParentA.idWithFallback != nullIdEvery30minParentA.idWithFallback
  }
}
