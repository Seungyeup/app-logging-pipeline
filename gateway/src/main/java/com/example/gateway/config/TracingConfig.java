package com.example.gateway.config;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TracingConfig {

    /**
     * Overrides the default ContextPropagators bean to include our custom propagator.
     * The @Primary annotation is crucial to ensure this bean is used instead of the one
     * provided by Spring Boot's auto-configuration.
     */
    @Bean
    @Primary
    public ContextPropagators primaryContextPropagators() {
        return ContextPropagators.create(TextMapPropagator.composite(
                // Our custom propagator runs first. If it finds X-Trace-Id, it creates the context.
                new TraceIdPropagator(),
                // If our propagator doesn't find the header, it falls back to the standard W3C propagator.
                W3CTraceContextPropagator.getInstance()
        ));
    }
}
