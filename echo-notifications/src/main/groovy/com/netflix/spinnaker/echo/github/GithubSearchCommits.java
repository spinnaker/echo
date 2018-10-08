package com.netflix.spinnaker.echo.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubSearchCommits {
  private int total_count;
}


