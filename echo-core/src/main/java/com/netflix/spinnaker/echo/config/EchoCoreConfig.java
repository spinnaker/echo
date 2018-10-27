package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.echo.artifacts.DefaultJinjavaFactory;
import com.netflix.spinnaker.echo.artifacts.JinjavaFactory;
import com.netflix.spinnaker.echo.discovery.DiscoveryPollingConfiguration;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.events.EventPropagator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Event Propagator
 */
@Configuration
@ComponentScan("com.netflix.spinnaker.echo.events")
@Import(DiscoveryPollingConfiguration.class)
public class EchoCoreConfig {
  private ApplicationContext context;

  @Autowired
  public EchoCoreConfig(ApplicationContext context) {
    this.context = context;
  }

  @Bean
  public EventPropagator propagator() {
    EventPropagator instance = new EventPropagator();
    for (EchoEventListener e : context.getBeansOfType(EchoEventListener.class).values()) {
      instance.addListener(e);
    }
    return instance;
  }

  @Bean
  @ConditionalOnMissingBean
  public JinjavaFactory jinjavaFactory() {
    return new DefaultJinjavaFactory();
  }
}
