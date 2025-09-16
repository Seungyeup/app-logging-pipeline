package com.example.demo.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    ObservationRegistryCustomizer<ObservationRegistry> addGlobalIdToAllObservations() {
        return registry -> registry.observationConfig().observationFilter(new ObservationFilter() {
            @Override
            public Observation.Context map(Observation.Context context) {
                String globalId = MDC.get("globalId");
                if (globalId != null && !globalId.isEmpty()) {
                    // Low-cardinality key values are propagated to span attributes by the Micrometer bridge
                    context.addLowCardinalityKeyValue(KeyValue.of("globalId", globalId));
                }
                return context;
            }
        });
    }
}
