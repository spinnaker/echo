package com.netflix.spinnaker.echo.events;

import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * responsible for sending events to classes that implement an EchoEventListener
 */
@Slf4j
@SuppressWarnings({"CatchException"})
public class EventPropagator {
  private List<EchoEventListener> listeners = new ArrayList<>();
  private Scheduler scheduler = Schedulers.io();

  public void addListener(EchoEventListener listener) {
    listeners.add(listener);
    log.info("Added listener " + listener.getClass().getSimpleName());
  }

  public void processEvent(Event event) {
    Observable.from(listeners)
      .map(listener ->
        AuthenticatedRequest.propagate(() -> {
          listener.processEvent(event);
          return null;
        })
      )
      .observeOn(scheduler)
      .subscribe(callable -> {
          try {
            callable.call();
          } catch (Exception e) {
            log.error("failed processing event: {}", event.content,  e);
          }
        }
      );
  }
}
