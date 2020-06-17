/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.config.TelemetryConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.io.IOException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit.RetrofitError
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isSuccess
import strikt.assertions.isTrue

@ExtendWith(MockKExtension::class)
class TelemetryEventListenerTest {

  @MockK
  private lateinit var telemetryService: TelemetryService

  private val registry = CircuitBreakerRegistry.ofDefaults()
  private val circuitBreaker = registry.circuitBreaker(TelemetryEventListener.TELEMETRY_REGISTRY_NAME)

  private lateinit var telemetryEventListener: TelemetryEventListener

  @BeforeEach
  fun setUp() {
    telemetryEventListener = TelemetryEventListener(
      telemetryService, TelemetryConfig.TelemetryConfigProps(), registry)
  }

  @Test
  fun `ignores events without details`() {
    val event = createLoggableEvent().apply {
      details = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without content`() {
    val event = createLoggableEvent().apply {
      content = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without type`() {
    val event = createLoggableEvent().apply {
      details.type = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  fun `ignores events with incorrect type`() {
    val event = createLoggableEvent().apply {
      details.type = "something boring happened"
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `ignores events without application ID`() {
    val event = createLoggableEvent().apply {
      details.application = null
    }

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService wasNot Called
    }
  }

  @Test
  fun `correctly populated event calls service`() {
    val event = createLoggableEvent()

    telemetryEventListener.processEvent(event)

    verify {
      telemetryService.log(any())
    }
  }

  @Test
  fun `RetrofitError from service is ignored`() {
    val event = createLoggableEvent()

    every { telemetryService.log(any()) } throws
      RetrofitError.networkError("url", IOException("network error"))

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
  }

  @Test
  fun `IllegalStateException from service is ignored`() {
    val event = createLoggableEvent()

    every { telemetryService.log(any()) } throws IllegalStateException("bad state")

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
  }

  @Test
  fun `circuit breaker is used`() {
    val event = createLoggableEvent()

    circuitBreaker.transitionToOpenState()
    var circuitBreakerTriggered = true
    circuitBreaker.eventPublisher.onCallNotPermitted { circuitBreakerTriggered = true }

    every { telemetryService.log(any()) } throws
      RetrofitError.networkError("url", IOException("network error"))

    expectCatching {
      telemetryEventListener.processEvent(event)
    }.isSuccess()
    expectThat(circuitBreakerTriggered).isTrue()
  }

  private fun createLoggableEvent(): Event {
    return Event().apply {
      details = Metadata().apply {
        type = "orca:orchestration:complete"
        application = "application"
      }
      content = mapOf()
    }
  }
}
