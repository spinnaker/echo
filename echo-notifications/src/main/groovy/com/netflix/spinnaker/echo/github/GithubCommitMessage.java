package com.netflix.spinnaker.echo.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubCommitMessage {
  private List<GithubCommitParents> parents;

  public GithubCommitMessage() {}

  public GithubCommitMessage(List<GithubCommitParents> parents) {
    this.parents = parents;
  }
}
