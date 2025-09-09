package com.example.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String GLOBAL_ID_HEADER = "X-Global-ID";
    private static final String MDC_KEY = "globalId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String globalId = request.getHeader(GLOBAL_ID_HEADER);
            if (globalId == null || globalId.isEmpty()) {
                globalId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_KEY, globalId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
