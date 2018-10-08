package com.netflix.spinnaker.echo.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubCommitParents {
  private String sha;

  public GithubCommitParents() {}

  public GithubCommitParents(String sha) {
    this.sha = sha;
  }
}


