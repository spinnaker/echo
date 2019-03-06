package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TriggerRepositorySpec extends Specification {
  private static final String TRIGGER_ID = '74f13df7-e642-4f8b-a5f2-0d5319aa0bd1'
  private static final String ACTION_INSTANCE_ID = "2d05822d-0275-454b-9616-361bf3b557ca:com.netflix.scheduledactions.ActionInstance:${TRIGGER_ID}"

  @Shared Trigger triggerA, triggerB, triggerC
  @Shared Trigger triggerD = Trigger.builder().id('123-789').build() // not assigned to any pipeline
  @Shared Pipeline pipelineA, pipelineB, pipelineC
  @Shared TriggerRepository repo
  @Shared List<Pipeline> pipelines

  def setupSpec() {
    Trigger triggerA = Trigger.builder().id('123-456').build()
    Trigger triggerB = Trigger.builder().id('456-789').build()
    Trigger triggerC = Trigger.builder().id(null).build() // to test the fallback mechanism

    Pipeline pipelineA = Pipeline.builder().application('app').name('pipeA').id('idPipeA').triggers([triggerA]).build()
    Pipeline pipelineB = Pipeline.builder().application('app').name('pipeB').id('idPipeB').triggers([triggerB, triggerC]).build()
    Pipeline pipelineC = Pipeline.builder().application('app').name('pipeC').build()

    pipelines = PipelineCache.decorateTriggers([pipelineA, pipelineB, pipelineC])

    this.pipelineA = pipelines[0]
    this.pipelineB = pipelines[1]
    this.pipelineC = pipelines[2]

    this.triggerA = this.pipelineA.triggers[0]
    this.triggerB = this.pipelineB.triggers[0]
    this.triggerC = this.pipelineB.triggers[1]
  }

  def setup() {
    repo = new TriggerRepository(pipelines)
  }

  @Unroll
  def 'looking up id #id in repo should return trigger #trigger'() {
    when:
    Trigger result = repo.getTrigger(id)

    then:
    result == trigger
    result?.parent == pipeline

    where:
    id          || trigger  | pipeline
    triggerA.id || triggerA | pipelineA
    triggerB.id || triggerB | pipelineB
    triggerC.id || triggerC | pipelineB
    triggerD.id || null     | null      // not in our repo
  }


  @Unroll
  def 'we can remove triggers by id'() {
    given:
    TriggerRepository repo = new TriggerRepository([pipelineA, pipelineB, pipelineC])

    when: 'we remove using a trigger id directly'
    Trigger removed = repo.remove(triggerA.id)

    then: 'it is effectively removed'
    removed == triggerA
    repo.triggers().size() == 2
    !repo.triggers().contains(triggerA)

    when: 'we remove using a compound id'
    removed = repo.remove("${pipelineB.id}:com.netflix.scheduledactions.ActionInstance:${triggerB.id}")

    then: 'it is also effectively removed'
    removed == triggerB
    repo.triggers().size() == 1
    repo.triggers().contains(triggerC)

    when: 'we remove a thing that is not there'
    removed = repo.remove(triggerA.id)

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
    Pipeline thePipe = Pipeline.builder().application('app').name('pipe').id('pipeId').triggers([every5minNoParent]).build()
    Trigger decoratedTrigger = PipelineCache.decorateTriggers([thePipe])[0].triggers[0]

    then: 'it would be overridden by the fallback id'
    decoratedTrigger.id != every5minNoParent.id


    when: 'two triggers have no id, no parent and the same cron expression'
    Trigger nullIdEvery5minNoParentA = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(null).build()
    Trigger nullIdEvery5minNoParentB = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(null).build()

    then: 'they get the same fallback id'
    nullIdEvery5minNoParentA.generateFallbackId() == nullIdEvery5minNoParentB.generateFallbackId()


    when: 'two triggers have different parents'
    Trigger nullIdEvery5minParentA = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(pipelineA).build()
    Trigger nullIdEvery5minParentB = Trigger.builder().id(null).cronExpression('*/5 * * * *').parent(pipelineB).build()

    then: 'they get different fallback ids'
    nullIdEvery5minParentA.generateFallbackId() != nullIdEvery5minParentB.generateFallbackId()


    when: 'two triggers have a different cron expression'
    Trigger nullIdEvery30minParentA = Trigger.builder().id(null).cronExpression('*/30 * * * *').parent(pipelineA).build()

    then: 'they get different fallback ids'
    nullIdEvery5minParentA.generateFallbackId() != nullIdEvery30minParentA.generateFallbackId()
  }
}
