package com.example.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String GLOBAL_ID_HEADER = "X-Global-ID";
    private static final String MDC_KEY = "globalId";

    private final Tracer tracer;

    public MdcLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        BaggageInScope baggage = null;
        try {
            String globalId = request.getHeader(GLOBAL_ID_HEADER);
            if (globalId == null || globalId.isEmpty()) {
                globalId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_KEY, globalId);
            // Put into tracing baggage so that all spans can tag from it
            try {
                baggage = tracer.createBaggage(MDC_KEY, globalId).makeCurrent();
            } catch (Exception ignored) {
                // tracer might not be initialized in some contexts; ignore
            }
            filterChain.doFilter(request, response);
        } finally {
            if (baggage != null) {
                try { baggage.close(); } catch (Exception ignored) {}
            }
            MDC.remove(MDC_KEY);
        }
    }
}
