/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.pagerduty.PagerDutyService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("pager-duty.enabled")
public class PagerDutyConfig {
  private static final Logger log = LoggerFactory.getLogger(PagerDutyConfig.class);

  private final String endpoint;

  public PagerDutyConfig(
      @Value("${pager-duty.endpoint:https://events.pagerduty.com}") String endpoint) {
    this.endpoint = endpoint;
  }

  @Bean
  PagerDutyService pagerDutyService(OkHttp3ClientConfiguration okHttpClientConfig) {
    log.info("Pager Duty service loaded");

    return new Retrofit.Builder()
        .baseUrl(endpoint)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(PagerDutyService.class);
  }
}
