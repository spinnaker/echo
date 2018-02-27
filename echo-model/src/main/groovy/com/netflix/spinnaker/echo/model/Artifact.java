/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class Artifact extends com.netflix.spinnaker.kork.artifacts.model.Artifact {

  @Override
  public Map<String, Object> getMetadata() {
    return super.getMetadata();
  }

  @Override
  public void setMetadata(Map<String, Object> metadata) {
    super.setMetadata(metadata);
  }

  // Add extra data in the JSON from Igor to metadata:
  @JsonAnySetter
  public void putMetadata(String key, Object value) {
    if (super.getMetadata() == null) {
      super.setMetadata(new HashMap<>());
    }
    super.getMetadata().put(key, value);
  }

}
