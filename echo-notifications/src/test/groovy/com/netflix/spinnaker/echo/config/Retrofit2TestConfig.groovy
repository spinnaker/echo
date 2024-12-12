package com.netflix.spinnaker.echo.config

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.config.OkHttpMetricsInterceptorProperties
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.client.Client

@Configuration
class Retrofit2TestConfig {
  @Autowired
  ObjectFactory<OkHttpClient.Builder> httpClientBuilderFactory

  @MockBean
  Client client

  @Bean
  RestAdapter.LogLevel getRetrofitLogLevel() {
    return RestAdapter.LogLevel.BASIC
  }

  @Bean
  OkHttpClientConfigurationProperties okHttpClientConfigurationProperties() {
    return new OkHttpClientConfigurationProperties()
  }

  @Bean
  OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties() {
    return new OkHttpMetricsInterceptorProperties()
  }

  @Bean
  Registry registry() {
    return new NoopRegistry();
  }

  @Bean
  OkHttp3MetricsInterceptor okHttp3MetricsInterceptor(Registry registry, OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties) {
    return new OkHttp3MetricsInterceptor(() -> registry, okHttpMetricsInterceptorProperties)
  }

  @Bean
  SpinnakerRequestHeaderInterceptor getSpinnakerRequestHeaderInterceptor() {
    return new SpinnakerRequestHeaderInterceptor(false)
  }

  @Bean
  OkHttp3ClientConfiguration okHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties, OkHttp3MetricsInterceptor okHttp3MetricsInterceptor, HttpLoggingInterceptor.Level retrofit2LogLevel, SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor) {
    return new OkHttp3ClientConfiguration(okHttpClientConfigurationProperties, okHttp3MetricsInterceptor, retrofit2LogLevel, spinnakerRequestHeaderInterceptor, httpClientBuilderFactory)
  }
}

@Configuration
class Retrofit2BasicLogTestConfig {
  @Bean
  HttpLoggingInterceptor.Level retrofit2LogLevel(){
    return HttpLoggingInterceptor.Level.BASIC;
  }
}

@Configuration
class Retrofit2HeadersLogTestConfig {
  @Bean
  HttpLoggingInterceptor.Level retrofit2LogLevel(){
    return HttpLoggingInterceptor.Level.HEADERS;
  }
}

@Configuration
class Retrofit2NoneLogTestConfig {
  @Bean
  HttpLoggingInterceptor.Level retrofit2LogLevel(){
    return HttpLoggingInterceptor.Level.NONE;
  }
}
