/*
 * Copyright 2025 Contributors to the Spinnaker project
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

package com.netflix.spinnaker.echo.googlechat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GoogleChatNotificationServiceTest {

  @Mock private GoogleChatService chatService;

  @Mock private NotificationTemplateEngine notificationTemplateEngine;

  @InjectMocks private GoogleChatNotificationService service;

  private Notification notification;

  @BeforeEach
  public void setup() {
    notification = new Notification();
    notification.setTo(Arrays.asList("https://chat.googleapis.com/v1/spaces/test"));
    
    when(notificationTemplateEngine.build(any(), any())).thenReturn("Test message");
  }

  @Test
  public void shouldHandleSimpleUrl() {
    // Given a notification with a simple URL
    
    // When
    service.handle(notification);
    
    // Then
    verify(chatService, times(1)).sendMessage(eq("test"), any(GoogleChatMessage.class));
  }
  
  @Test
  public void shouldHandleUrlWithQueryParameters() {
    // Given a notification with a URL containing query parameters
    notification.setTo(Arrays.asList("https://chat.googleapis.com/v1/spaces/test?key=abc&token=123"));
    
    // When
    service.handle(notification);
    
    // Then
    verify(chatService, times(1)).sendMessage(eq("test?key=abc&token=123"), any(GoogleChatMessage.class));
  }
}
