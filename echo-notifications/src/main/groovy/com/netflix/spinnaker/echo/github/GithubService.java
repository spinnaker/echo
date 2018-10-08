/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.github;

import retrofit.client.Response;
import retrofit.http.*;

public interface GithubService {
  @POST("/api/v3/repos/{repo}/statuses/{sha}")
  Response updateCheck(
    @Header("Authorization") String token,
    @Path(value = "repo", encode = false) String repo,
    @Path("sha") String sha,
    @Body GithubStatus status);

  @GET("/api/v3/repos/{repo}/commits/{sha}")
  Response getCommit(
    @Header("Authorization") String token,
    @Path(value = "repo", encode = false) String repo,
    @Path("sha") String sha);

  // curl -X GET
  // -H "Accept: application/vnd.github.cloak-preview"
  // "https://github.schibsted.io/api/v3/search/commits
  // &q=repo:spt-infra-delivery-test/k8s-v2-hello-world+hash=aede6867d774af7ea5cbf962f2876f25df141e73"

  @Headers("Accept: application/vnd.github.cloak-preview")
  @GET("/api/v3/search/commits")
  Response searchCommits(
    @Header("Authorization") String token,
    @Query(value = "q", encodeValue = false) String query);
}
