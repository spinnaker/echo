/*
 * Copyright 2023 Armory.
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
 */

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.config.RestProperties;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.events.RestEventListener;
import com.netflix.spinnaker.echo.events.RestEventService;
import com.netflix.spinnaker.echo.events.SimpleEventTemplateEngine;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestEventListenerTest {

  private RestEventListener listener;
  private Event event;
  private RestService restService;

  @BeforeEach
  void setup() {
    listener =
        new RestEventListener(
            new RestUrls(),
            new SimpleEventTemplateEngine(),
            new RestEventService(new RetrySupport()),
            new NoopRegistry());

    event = new Event();
    event.setContent(Map.of("uno", "dos"));

    restService = Mockito.mock(RestService.class);

    listener.setEventName("defaultEvent");
    listener.setFieldName("defaultField");
  }

  @Test
  void renderTemplateWhenTemplateIsSet() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setTemplate("{\"myCustomEventField\":{{event}} }");
    config.setWrap(true);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("myCustomEventField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void wrapsEventsWhenWrapIsSet() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(true);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("eventName", listener.getEventName());
                  expected.put("defaultField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void canOverwriteWrapFieldFor() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(true);
    config.setFieldName("myField");
    config.setEventName("myEventName");

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("eventName", "myEventName");
                  expected.put("myField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void canDisableWrappingOfEvents() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test
  void sendsEventsToMultipleHosts() {
    RestService restService2 = Mockito.mock(RestService.class);

    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);

    RestUrls.Service service1 =
        RestUrls.Service.builder().client(restService).config(config).build();

    RestUrls.Service service2 =
        RestUrls.Service.builder().client(restService2).config(config).build();

    listener.getRestUrls().setServices(List.of(service1, service2));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test()
  void exceptionInSendingEventToOneHostDoesNotAffectSecondHost() {
    RestService restService2 = Mockito.mock(RestService.class);

    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);
    config.setRetryCount(3);

    RestUrls.Service service1 =
        RestUrls.Service.builder().client(restService).config(config).build();

    RestUrls.Service service2 =
        RestUrls.Service.builder().client(restService2).config(config).build();

    listener.getRestUrls().setServices(List.of(service1, service2));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    Mockito.when(restService.recordEvent(expectedEvent)).thenThrow(new RuntimeException());

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(3)).recordEvent(expectedEvent);
    Assertions.assertThrows(RuntimeException.class, () -> restService.recordEvent(expectedEvent));
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test
  void shouldSendEventWhenCircuitBreakerIsEnabled() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.setCircuitBreakerEnabled(true);
    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
  }
}
