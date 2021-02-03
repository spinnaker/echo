/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.microsoftteams;

import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;

@Slf4j
public class MicrosoftTeamsService {
  private static final String BASE_URL_REGEX_PATTERN = "^(http|https)://.+?/";

  private Client retrofitClient;
  private RestAdapter.LogLevel retrofitLogLevel;

  public MicrosoftTeamsService(Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    this.retrofitClient = retrofitClient;
    this.retrofitLogLevel = retrofitLogLevel;
  }

  public Response sendMessage(String webhookUrl, MicrosoftTeamsMessage message) {
    MicrosoftTeamsClient microsoftTeamsClient =
        new RestAdapter.Builder()
            .setConverter(new JacksonConverter())
            .setClient(retrofitClient)
            .setEndpoint(getEndpointUrl(webhookUrl))
            .setLogLevel(retrofitLogLevel)
            .setLog(new Slf4jRetrofitLogger(MicrosoftTeamsClient.class))
            .build()
            .create(MicrosoftTeamsClient.class);

    return microsoftTeamsClient.sendMessage(getRelativePath(webhookUrl), message);
  }

  private String getEndpointUrl(String webhookUrl) {
    String baseUrl = "";
    Pattern pattern = Pattern.compile(BASE_URL_REGEX_PATTERN, Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(webhookUrl);

    if (matcher.find()) {
      baseUrl = matcher.group(0);
    } else {
      log.error("Webhook URL does not match base URL format. URL: " + webhookUrl);
    }

    return baseUrl;
  }

  private String getRelativePath(String webhookUrl) {
    String baseUrl = getEndpointUrl(webhookUrl);
    String relativePath = webhookUrl.substring(baseUrl.length());

    // Remove slash from beginning of path as the client will prefix the string with a slash
    if (relativePath.charAt(0) == '/') {
      relativePath = relativePath.substring(1, relativePath.length());
    }

    return relativePath;
  }
}
