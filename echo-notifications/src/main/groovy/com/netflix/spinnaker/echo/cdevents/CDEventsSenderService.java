/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/
package com.netflix.spinnaker.echo.cdevents;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.http.HttpMessageFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.springframework.stereotype.Component;

@Component
public class CDEventsSenderService {

  /**
   * Sends CDEvent to the Configured ClouEvent broker URL.
   *
   * @param ceToSend
   * @param url
   * @return HttpURLConnection
   * @throws IOException
   */
  public HttpURLConnection sendCDEvent(CloudEvent ceToSend, URL url) throws IOException {
    HttpURLConnection httpUrlConnection = createConnection(url);
    MessageWriter messageWriter = createMessageWriter(httpUrlConnection);
    messageWriter.writeBinary(ceToSend);

    return httpUrlConnection;
  }

  /**
   * @param url
   * @return httpUrlConnection
   * @throws IOException
   */
  private HttpURLConnection createConnection(URL url) throws IOException {
    HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
    httpUrlConnection.setRequestMethod("POST");
    httpUrlConnection.setDoOutput(true);
    httpUrlConnection.setDoInput(true);
    return httpUrlConnection;
  }

  /**
   * @param httpUrlConnection
   * @return messageWriter
   */
  private MessageWriter createMessageWriter(HttpURLConnection httpUrlConnection) {
    return HttpMessageFactory.createWriter(
        httpUrlConnection::setRequestProperty,
        body -> {
          try {
            if (body != null) {
              httpUrlConnection.setRequestProperty("content-length", String.valueOf(body.length));
              try (OutputStream outputStream = httpUrlConnection.getOutputStream()) {
                outputStream.write(body);
              }
            } else {
              httpUrlConnection.setRequestProperty("content-length", "0");
            }
          } catch (IOException t) {
            throw new UncheckedIOException(t);
          }
        });
  }
}
