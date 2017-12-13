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

import java.util.List;

public interface HolidayCalendarProvider {
  boolean isHoliday();
  List<String> getHolidays();

  default boolean isHoliday(List<Holiday> holidays, int dayOfYear) {
    return holidays
      .stream()
      .anyMatch(holiday -> holiday.getStart() <= dayOfYear && dayOfYear < holiday.getEnd());
  }
}
