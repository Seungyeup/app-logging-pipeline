package com.example.gateway.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

public class TraceIdPropagator implements TextMapPropagator {

    private static final Logger log = LoggerFactory.getLogger(TraceIdPropagator.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Collection<String> FIELDS = Collections.singletonList(TRACE_ID_HEADER);

    @Override
    public Collection<String> fields() {
        return FIELDS;
    }

    @Override
    public <C> void inject(@Nonnull Context context, C carrier, @Nonnull TextMapSetter<C> setter) {
        SpanContext spanContext = Span.fromContext(context).getSpanContext();
        if (spanContext.isValid()) {
            log.info("Injecting traceId {} into headers.", spanContext.getTraceId());
            setter.set(carrier, TRACE_ID_HEADER, spanContext.getTraceId());
        }
    }

    @Override
    public <C> Context extract(@Nonnull Context context, C carrier, @Nonnull TextMapGetter<C> getter) {
        log.info("Attempting to extract trace context...");
        String traceId = getter.get(carrier, TRACE_ID_HEADER);
        log.info("Found '{}' header: {}", TRACE_ID_HEADER, traceId);

        if (traceId == null || traceId.isEmpty() || traceId.length() != 32) {
            log.info("Header not found or invalid. Returning original context.");
            return context;
        }

        log.info("Valid header found. Creating new SpanContext with traceId: {}", traceId);
        SpanContext spanContext = SpanContext.createFromRemoteParent(
                traceId,
                "0000000000000001", // Placeholder spanId
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        return context.with(Span.wrap(spanContext));
    }
}