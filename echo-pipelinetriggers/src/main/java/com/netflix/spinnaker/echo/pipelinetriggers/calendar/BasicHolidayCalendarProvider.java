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

import com.netflix.spinnaker.echo.config.CalendarConfigurationProperties.Basic.HolidaySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

public class BasicHolidayCalendarProvider implements HolidayCalendarProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(BasicHolidayCalendarProvider.class);
  private List<Holiday> holidays;
  private Calendar calendar;
  public BasicHolidayCalendarProvider(List<HolidaySpec> holidaySpecs,
                                      Calendar calendar) {
    this.holidays = buildHolidays(holidaySpecs, now().get(Calendar.YEAR));
    this.calendar = calendar;
  }

  @Override
  public boolean isHoliday() {
    return isHoliday(now());
  }

  private boolean isHoliday(Calendar calendar) {
    return isHoliday(holidays, calendar.get(java.util.Calendar.DAY_OF_YEAR));
  }

  @Override
  public List<String> getHolidays() {
    return holidays
      .stream()
      .map(Holiday::getName)
      .collect(Collectors.toList());
  }

  private List<Holiday> buildHolidays(List<HolidaySpec> holidaySpecs, int year) {
    List<Holiday> holidays = new ArrayList<>();
    for (HolidaySpec config : holidaySpecs) {
      if (config.getWeekInMonth() != null) {
        holidays.add(
          newHoliday(config.getName(), dayOfYear(year, config.getMonth() - 1, config.getDay(), config.getWeekInMonth(), now())
        ));
      } else {
        holidays.add(
          newHoliday(config.getName(), dayOfYear(year, config.getMonth() - 1, config.getDay(), now()))
        );
      }
    }

    LOGGER.info("Loaded Basic Calendar holidays {}", holidays);

    return holidays;
  }

  private java.util.Calendar now() {
    return calendar != null ? (java.util.Calendar) calendar.clone() : java.util.Calendar.getInstance();
  }

  private static int dayOfYear(int year, int month, int dayOfWeek, int weekInMonth, Calendar calendar) {
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
    calendar.set(Calendar.DAY_OF_WEEK_IN_MONTH, weekInMonth);
    return calendar.get(Calendar.DAY_OF_YEAR);
  }

  private static int dayOfYear(int year, int month, int day, Calendar calendar) {
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    return calendar.get(Calendar.DAY_OF_YEAR);
  }

  private Holiday newHoliday(String name, int dayOfYear) {
    return new Holiday(dayOfYear, dayOfYear + 1, name);
  }
}
