package com.netflix.spinnaker.echo.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TriggerListResponse {
  List<TriggerDescription> pipeline;

  List<TriggerDescription> manuallyCreated;
}
