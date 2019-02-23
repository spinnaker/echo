/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.echo.events.RestClientFactory;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

import static retrofit.Endpoints.newFixedEndpoint;

@ComponentScan("com.netflix.spinnaker.echo.artifacts")
@Configuration
@EnableConfigurationProperties(ArtifactEmitterProperties.class)
public class ArtifactEmitterConfig {

  @Bean
  public RestAdapter.LogLevel retrofitLogLevel(@Value("${retrofit.logLevel:BASIC}") String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  @ConditionalOnExpression("${artifact-emitter.enabled:false}")
  public ArtifactEmitterUrls artifactRestServices(
    ArtifactEmitterProperties artifactEmitterProperties,
    RestClientFactory clientFactory,
    Ok3Client ok3Client,
    RestAdapter.LogLevel retrofitLogLevel
  ) {
    ArtifactEmitterUrls artifactEmitterUrls = new ArtifactEmitterUrls();

    for (ArtifactEndpointConfiguration endpoint : artifactEmitterProperties.endpoints) {

      RestAdapter.Builder restAdapterBuilder = new RestAdapter.Builder()
        .setEndpoint(newFixedEndpoint(endpoint.getUrl()))
        .setClient(endpoint.internal ? ok3Client : clientFactory.getClient(endpoint.insecure))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(RestService.class))
        .setConverter(new JacksonConverter());

      artifactEmitterUrls.services.add(new Service(restAdapterBuilder.build().create(RestService.class), endpoint));
    }

    return artifactEmitterUrls;
  }
}
