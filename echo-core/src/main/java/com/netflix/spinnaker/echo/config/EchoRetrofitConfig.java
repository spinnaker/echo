/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter.LogLevel;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class EchoRetrofitConfig {
  private int maxIdleConnections;
  private int keepAliveDurationMs;
  private boolean retryOnConnectionFailure;

  public EchoRetrofitConfig(
    @Value("${okHttpClient.connectionPool.maxIdleConnections:5}") int maxIdleConnections,
    @Value("${okHttpClient.connectionPool.keepAliveDurationMs:300000}") int keepAliveDurationMs,
    @Value("${okHttpClient.retryOnConnectionFailure:true}") boolean retryOnConnectionFailure
  ) {
    this.maxIdleConnections = maxIdleConnections;
    this.keepAliveDurationMs = keepAliveDurationMs;
    this.retryOnConnectionFailure = retryOnConnectionFailure;
  }

  @Bean
  public Ok3Client ok3Client(OkHttp3ClientConfiguration okHttpClientConfig) {
    okhttp3.OkHttpClient.Builder builder = okHttpClientConfig
      .create()
      .connectionPool(new ConnectionPool(maxIdleConnections, keepAliveDurationMs, TimeUnit.MILLISECONDS))
      .retryOnConnectionFailure(retryOnConnectionFailure);
    return new Ok3Client(builder.build());
  }

  @Bean
  public LogLevel retrofitLogLevel() {
    return LogLevel.BASIC;
  }
}
