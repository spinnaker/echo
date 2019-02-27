package com.netflix.spinnaker.echo.config;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.echo.services.KeelService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.RestAdapter.LogLevel;

@Configuration
@Slf4j
public class KeelConfig {
  @Bean
  public LogLevel retrofitLogLevel(@Value("${retrofit.logLevel:BASIC}") String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  public Endpoint keelEndpoint(@Value("${keel.baseUrl}") String keelBaseUrl) {
    return Endpoints.newFixedEndpoint(keelBaseUrl);
  }

  @Bean
  public KeelService keelService(Endpoint keelEndpoint,
                                 Ok3Client ok3Client,
                                 LogLevel retrofitLogLevel) {
    return new RestAdapter.Builder()
      .setEndpoint(keelEndpoint)
      .setClient(ok3Client)
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(KeelService.class)).build()
      .create(KeelService.class);
  }
}
