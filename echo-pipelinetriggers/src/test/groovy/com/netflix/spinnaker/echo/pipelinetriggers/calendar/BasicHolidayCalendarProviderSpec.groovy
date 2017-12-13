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

package com.netflix.spinnaker.echo.pipelinetriggers.calendar

import spock.lang.Specification
import static com.netflix.spinnaker.echo.config.CalendarConfigurationProperties.Basic.*

class BasicHolidayCalendarProviderSpec extends Specification {
  def "should have a preset list of holidays"() {
    given:
    def provider = new BasicHolidayCalendarProvider(
      [new HolidaySpec(name: "Christmas", day: 25, month: 12)],
      Calendar.getInstance()
    )

    expect:
    provider.getHolidays() == ["Christmas"]
  }

  def "should determine if a date is a holiday"() {
    given:
    Calendar calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 25)
    calendar.set(Calendar.MONTH, Calendar.DECEMBER)
    calendar.set(Calendar.YEAR, 2017)
    def provider = new BasicHolidayCalendarProvider(
      [new HolidaySpec(name: "Christmas", day: 25, month: 12)],
      calendar
    )

    expect:
    provider.isHoliday()
  }

  def "should determine if a date is not a holiday"() {
    given:
    Calendar calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.MONTH, Calendar.DECEMBER)
    def provider = new BasicHolidayCalendarProvider(
      [
        new HolidaySpec(name: "Christmas", day: 25, month: 12),
        new HolidaySpec(name: "New Year", day: 1, month: 1)
      ],
      calendar
    )

    expect:
    !provider.isHoliday()
  }

}
