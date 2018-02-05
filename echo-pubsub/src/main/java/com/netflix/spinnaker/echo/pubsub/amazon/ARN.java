/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pubsub.amazon;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ARN { //todo eb: should the class in lifecycle in clouddriver actually just be made public?

  static final Pattern PATTERN = Pattern.compile("arn:aws(?:-cn|-us-gov)?:.*:(.*):(\\d+):(.*)");

  String arn;
  String region;
  String name;

  NetflixAmazonCredentials account;

  ARN(String arn) {
    this.arn = arn;

    Matcher sqsMatcher = PATTERN.matcher(arn);
    if (!sqsMatcher.matches()) {
      throw new IllegalArgumentException(arn + " is not a valid SNS or SQS ARN");
    }

    this.region = sqsMatcher.group(1);
    this.name = sqsMatcher.group(3);
  }

}
