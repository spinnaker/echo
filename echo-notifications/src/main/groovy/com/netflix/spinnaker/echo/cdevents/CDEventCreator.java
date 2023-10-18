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

public abstract class CDEventCreator {
  abstract CloudEvent createCDEvent();

  private String source;
  private String subjectId;
  private String subjectSource;
  private String subjectUrl;

  public CDEventCreator(String source, String subjectId, String subjectSource, String subjectUrl) {
    this.source = source;
    this.subjectId = subjectId;
    this.subjectSource = subjectSource;
    this.subjectUrl = subjectUrl;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(String subjectId) {
    this.subjectId = subjectId;
  }

  public String getSubjectSource() {
    return subjectSource;
  }

  public void setSubjectSource(String subjectSource) {
    this.subjectSource = subjectSource;
  }

  public String getSubjectUrl() {
    return subjectUrl;
  }

  public void setSubjectUrl(String subjectUrl) {
    this.subjectUrl = subjectUrl;
  }
}
