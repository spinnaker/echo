package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import spock.lang.Specification

class EventHandlerSupport extends Specification {
  public PipelineCache pipelineCache(Pipeline... pipelines) {
    return pipelineCache(Arrays.asList(pipelines))
  }

  public PipelineCache pipelineCache(List<Pipeline> pipelines) {
    def cache = Mock(PipelineCache)
    def decoratedPipelines = PipelineCache.decorateTriggers(pipelines)
    cache.getEnabledTriggersSync() >> PipelineCache.extractEnabledTriggersFrom(decoratedPipelines)
    return cache
  }
}
