package com.example.demo;

import com.example.demo.entity.LogEntry;
import com.example.demo.service.LogSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @Autowired
    private LogSaveService logSaveService;

    @GetMapping("/api/hello")
    public String hello() {
        log.info("Hello API called.");

        // Get traceId from the current span context
        String traceId = io.opentelemetry.api.trace.Span.current().getSpanContext().getTraceId();

        // Create and save LogEntry
        LogEntry logEntry = new LogEntry(
                traceId,
                "Hello API call received",
                LocalDateTime.now(),
                "INFO"
        );
        logSaveService.saveLog(logEntry);
        log.info("LogEntry saved to DB with traceId: {}", traceId);

        return "Hello from Spring Boot!";
    }

    @GetMapping("/api/hello2")
    public String hello2() {
        log.info("Hello API 2 called.");

        // Get traceId from the current span context
        String traceId = io.opentelemetry.api.trace.Span.current().getSpanContext().getTraceId();

        // Create and save LogEntry
        LogEntry logEntry = new LogEntry(
                traceId,
                "Hello API call received",
                LocalDateTime.now(),
                "INFO"
        );
        logSaveService.saveLog(logEntry);
        log.info("LogEntry saved to DB with traceId: {}", traceId);

        return "Hello from Spring Boot!";
    }
}
