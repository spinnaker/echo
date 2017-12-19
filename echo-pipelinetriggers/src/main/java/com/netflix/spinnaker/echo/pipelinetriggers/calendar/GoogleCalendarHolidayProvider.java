/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.calendar;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GoogleCalendarHolidayProvider implements HolidayCalendarProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCalendarHolidayProvider.class);
  private final GoogleCalendarCredentialProvider googleCalendarCredentialProvider;
  private LoadingCache<String, List<Holiday>> holidayCache;
  private String calendarId;
  private java.util.Calendar calendar;

  public GoogleCalendarHolidayProvider(GoogleCalendarCredentialProvider googleCalendarCredentialProvider,
                                       java.util.Calendar calendar,
                                       String calendarId) {
    this.googleCalendarCredentialProvider = googleCalendarCredentialProvider;
    this.calendar = calendar;
    this.calendarId = calendarId;
    this.holidayCache = buildHolidayCache(TimeUnit.DAYS.toMillis(30));
  }

  public boolean isHoliday() {
    int dayOfYear = now().get(java.util.Calendar.DAY_OF_YEAR);
    try {
      return isHoliday(holidayCache.get(calendarId), dayOfYear);
    } catch (ExecutionException e) {
      LOGGER.error("failed to determine if {} is a holiday", dayOfYear);
    }

    return false;
  }

  public List<String> getHolidays() {
    try {
      return holidayCache.get(calendarId)
        .stream()
        .map(Holiday::getName)
        .collect(Collectors.toList());
    } catch (ExecutionException e) {
      LOGGER.error("failed to get holidays");
    }

    return Collections.emptyList();
  }

  private LoadingCache<String, List<Holiday>> buildHolidayCache(long ttl) {
    return CacheBuilder.newBuilder()
      .expireAfterAccess(ttl, TimeUnit.MILLISECONDS)
      .build(new CacheLoader<String, List<Holiday>>() {
        @Override
        public List<Holiday> load(String key) throws Exception {
          return loadHolidays(key);
        }
      });
  }

  private long getCalendarTtlMillis() {
    return TimeUnit.DAYS.toMillis(30);
  }

  private List<Holiday> loadHolidays(String calendarId) {
    List<Holiday> holidays = new ArrayList<>();
    try {
      GoogleCredential credential = GoogleCredential.fromStream(googleCalendarCredentialProvider.getCredentials())
        .createScoped(Collections.singleton(CalendarScopes.CALENDAR_READONLY));
      Calendar service = new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), credential)
        .setApplicationName("echo")
        .build();

      String pageToken = null;
      do {
        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = service.events()
          .list(calendarId)
          .setPageToken(pageToken)
          .setFields("items(end,start,summary)")
          .setTimeMin(now)
          .setTimeMax(new DateTime(now.getValue() + getCalendarTtlMillis()))
          .execute();
        List<Event> items = events.getItems();
        for (Event event : items) {
          Holiday holiday = new Holiday(
            convertEventDateTimeToDayOfYear(event.getStart()),
            convertEventDateTimeToDayOfYear(event.getEnd()),
            event.getSummary()
          );

          holidays.add(holiday);
        }

        pageToken = events.getNextPageToken();
      } while (pageToken != null);
      LOGGER.info("Loaded Google Calendar holidays {}", holidays);
    } catch (IOException | GeneralSecurityException e) {
      LOGGER.error("failed to load holidays", e);
    }

    return holidays;
  }

  private java.util.Calendar now() {
    return calendar != null ? (java.util.Calendar) calendar.clone() : java.util.Calendar.getInstance();
  }

  private int convertEventDateTimeToDayOfYear(EventDateTime eventDateTime) {
    java.util.Calendar calendar = now();
    calendar.setTimeInMillis(eventDateTime.getDate().getValue() - calendar.getTimeZone().getRawOffset());
    return calendar.get(java.util.Calendar.DAY_OF_YEAR);
  }
}
