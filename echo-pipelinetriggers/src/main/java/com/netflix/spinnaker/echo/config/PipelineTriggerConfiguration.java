package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.pipelinetriggers.calendar.GoogleCalendarCredentialProvider;
import com.netflix.spinnaker.echo.pipelinetriggers.calendar.GoogleCalendarHolidayProvider;
import com.netflix.spinnaker.echo.pipelinetriggers.calendar.BasicHolidayCalendarProvider;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;

@Configuration
@EnableConfigurationProperties(CalendarConfigurationProperties.class)
@ComponentScan("com.netflix.spinnaker.echo.pipelinetriggers")
@Slf4j
public class PipelineTriggerConfiguration {
  private Client retrofitClient;

  @Autowired
  public void setRetrofitClient(OkHttpClient okHttpClient) {
    this.retrofitClient = new OkClient(okHttpClient);
  }

  @Bean
  public OrcaService orca(@Value("${orca.baseUrl}") final String endpoint) {
    return bindRetrofitService(OrcaService.class, endpoint);
  }

  @Bean
  public Scheduler scheduler() {
    return Schedulers.io();
  }

  @Bean
  public int pollingIntervalSeconds() {
    return 10;
  }

  @Bean
  public Client retrofitClient() {
    return new OkClient();
  }

  @Bean
  @ConditionalOnExpression("${pipelineTriggers.calendar.basic.enabled:false}")
  public BasicHolidayCalendarProvider staticHolidayCalendarProvider(CalendarConfigurationProperties calendarConfigurationProperties) {
    ZoneId zone = ZoneId.of(calendarConfigurationProperties.getTimezone());
    return new BasicHolidayCalendarProvider(
      calendarConfigurationProperties.getBasic().getHolidaySpecs(),
      Calendar.getInstance(TimeZone.getTimeZone(zone))
    );
  }

  @Bean
  @ConditionalOnExpression("${pipelineTriggers.calendar.google.enabled:false}")
  public GoogleCalendarHolidayProvider googleCalendarHolidayProvider(CalendarConfigurationProperties calendarConfigurationProperties) {
    ZoneId zone = ZoneId.of(calendarConfigurationProperties.getTimezone());
    String credentials = calendarConfigurationProperties.getGoogle().getCredentials();
    return new GoogleCalendarHolidayProvider(
      new GoogleCalendarCredentialProvider(credentials),
      Calendar.getInstance(TimeZone.getTimeZone(zone)),
      calendarConfigurationProperties.getGoogle().getCalendarId()
    );
  }

  private <T> T bindRetrofitService(final Class<T> type, final String endpoint) {
    log.info("Connecting {} to {}", type.getSimpleName(), endpoint);

    return new RestAdapter.Builder().setClient(retrofitClient)
                                    .setConverter(new JacksonConverter(new ObjectMapper()))
                                    .setEndpoint(endpoint)
                                    .setLog(new Slf4jRetrofitLogger(type))
                                    .build()
                                    .create(type);
  }
}
