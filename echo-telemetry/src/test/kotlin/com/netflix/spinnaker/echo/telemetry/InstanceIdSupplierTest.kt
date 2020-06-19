/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.config.TelemetryConfig.TelemetryConfigProps
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class InstanceIdSupplierTest {
  @Test
  fun `returns the configured value with no redis backend`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), null)

    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  @Test
  fun `returns the configured value when no current value in redis`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), getRedis())

    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  @Test
  fun `returns the value in redis if it exists`() {
    val redis = getRedis()
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), redis)
    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")

    val otherInstanceIdSupplier = InstanceIdSupplier(withInstanceId("my-new-id"), redis)
    expectThat(otherInstanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  @Test
  fun `returns the configured value if the selector is unable to find the configured redis`() {
    val instanceIdSupplier = InstanceIdSupplier(withInstanceId("my-id"), getMissingRedis())
    expectThat(instanceIdSupplier.uniqueId).isEqualTo("my-id")
  }

  private fun withInstanceId(id: String): TelemetryConfigProps {
    val result = TelemetryConfigProps()
    result.instanceId = id
    return result
  }

  private fun getRedis(): RedisClientSelector {
    val embeddedRedis = EmbeddedRedis.embed()
    val redisClientDelegate = JedisClientDelegate("primaryDefault", embeddedRedis.pool)
    return RedisClientSelector(listOf(redisClientDelegate))
  }

  private fun getMissingRedis(): RedisClientSelector {
    return RedisClientSelector(listOf())
  }
}
