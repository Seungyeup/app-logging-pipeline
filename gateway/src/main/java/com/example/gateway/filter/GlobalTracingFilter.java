package com.example.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class GlobalTracingFilter implements GlobalFilter, Ordered {

    private static final String FLUTTER_APP_SERVICE_NAME = "flutter-client-app";
    private final Tracer tracer;

    public GlobalTracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // By running this filter after the default tracing filter, a span is already created.
        // Our job is to simply modify it.
        Span currentSpan = this.tracer.currentSpan();

        if (currentSpan != null) {
            // If the request comes from an un-instrumented client (like our Flutter app),
            // the default instrumentation doesn't know the remote client's name.
            // We'll set it here to ensure it appears correctly in the service graph.
            currentSpan.remoteServiceName(FLUTTER_APP_SERVICE_NAME);

            // We can also enrich the span with more details if needed.
            String globalId = exchange.getRequest().getHeaders().getFirst("X-Global-ID");
            if (globalId != null && !globalId.isEmpty()) {
                currentSpan.tag("globalId", globalId);
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // This order needs to be AFTER the Observation starter's TraceWebFilter, which has an order of -2000.
        // By running after it, we ensure the span has already been created and is in context.
        return -1999;
    }
}
