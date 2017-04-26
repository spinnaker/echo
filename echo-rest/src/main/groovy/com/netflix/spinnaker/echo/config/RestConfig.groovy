/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config

import static retrofit.Endpoints.newFixedEndpoint

import org.apache.commons.codec.binary.Base64
import com.netflix.spinnaker.echo.rest.RestService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

/**
 * Rest endpoint configuration
 */
@Configuration
@ConditionalOnProperty('rest.enabled')
@CompileStatic
@SuppressWarnings('GStringExpressionWithinString')
class RestConfig {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  Client retrofitClient() {
    new OkClient()
  }

  @Bean
  LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean
  RestUrls restServices(RestProperties restProperties, Client retrofitClient, LogLevel retrofitLogLevel) {

    RestUrls restUrls = new RestUrls()

    restProperties

    restProperties.endpoints.each { RestProperties.RestEndpointConfiguration endpoint ->
      RestAdapter.Builder restAdapterBuilder = new RestAdapter.Builder()
        .setEndpoint(newFixedEndpoint(endpoint.url as String))
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setConverter(new JacksonConverter())


      Map<String, String> headers = new HashMap<>()

      if (endpoint.username && endpoint.password) {
        String auth = "Basic " + Base64.encodeBase64String("${endpoint.username}:${endpoint.password}".getBytes())
        headers["Authorization"] = auth
      }

      if (endpoint.headers) {
        headers += endpoint.headers
      }

      if (endpoint.headersFile) {
        new File(endpoint.headersFile).eachLine { line ->
          def pair = line.split(":")
          if (pair.length == 2) {
            headers[pair[0]]= pair[1].trim()
          }
        }
      }

      if (headers) {
        RequestInterceptor headerInterceptor = new RequestInterceptor() {
          @Override
          public void intercept(RequestInterceptor.RequestFacade request) {
            headers.each { k, v ->
              request.addHeader(k, v)
            }
          }
        }
        restAdapterBuilder.setRequestInterceptor(headerInterceptor)
      }

      restUrls.services.add(
        [
          client: restAdapterBuilder.build().create(RestService),
          config: endpoint
        ]
      )
    }

    restUrls
  }

}
