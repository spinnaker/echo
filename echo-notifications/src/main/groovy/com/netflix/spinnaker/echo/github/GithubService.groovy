/*
 * Copyright 2018 Schibsted ASA
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


package com.netflix.spinnaker.echo.github

import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.Header
import retrofit.http.POST
import retrofit.http.Path

interface GithubService {
  @POST('/api/v3/repos/{repo}/statuses/{sha}')
  Response updateCheck(
    @Header('Authorization') String token,
    @Path(value = 'repo', encode = false) String repo,
    @Path('sha') String sha,
    @Body GithubStatus status)
}
