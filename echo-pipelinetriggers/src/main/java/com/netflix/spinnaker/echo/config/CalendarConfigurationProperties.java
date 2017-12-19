/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("pipelineTriggers.calendar")
public class CalendarConfigurationProperties {
  private String timezone = "America/Los_Angeles";
  private Google google;
  private Basic basic;

  public static class Google {
    private String calendarId;
    private String credentials;
    boolean enabled;

    public String getCalendarId() {
      return calendarId;
    }

    public void setCalendarId(String calendarId) {
      this.calendarId = calendarId;
    }

    public String getCredentials() {
      return credentials;
    }

    public void setCredentials(String credentials) {
      this.credentials = credentials;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Basic {
    private boolean enabled;
    private List<HolidaySpec> holidaySpecs;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<HolidaySpec> getHolidaySpecs() {
      return holidaySpecs;
    }

    public void setHolidaySpecs(List<HolidaySpec> holidaySpecs) {
      this.holidaySpecs = holidaySpecs;
    }

    public static class HolidaySpec {
      private String name;
      private Integer month; //starts at 0
      private Integer weekInMonth;
      private Integer day;

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public Integer getMonth() {
        return month;
      }

      public void setMonth(Integer month) {
        this.month = month;
      }

      public Integer getWeekInMonth() {
        return weekInMonth;
      }

      public void setWeekInMonth(Integer weekInMonth) {
        this.weekInMonth = weekInMonth;
      }

      public Integer getDay() {
        return day;
      }

      public void setDay(Integer day) {
        this.day = day;
      }
    }
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public Google getGoogle() {
    return google;
  }

  public void setGoogle(Google google) {
    this.google = google;
  }

  public Basic getBasic() {
    return basic;
  }

  public void setBasic(Basic basic) {
    this.basic = basic;
  }
}
