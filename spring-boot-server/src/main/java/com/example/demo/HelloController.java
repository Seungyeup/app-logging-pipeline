package com.example.demo;

import com.example.demo.entity.LogEntry;
import com.example.demo.service.LogSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

        // Get globalId from MDC
        String globalId = MDC.get("globalId");
        if (globalId == null) {
            globalId = "unknown";
        }

        // Create and save LogEntry
        LogEntry logEntry = new LogEntry(
                globalId,
                "Hello API call received",
                LocalDateTime.now(),
                "INFO"
        );
        logSaveService.saveLog(logEntry);
        log.info("LogEntry saved to DB with globalId: {}", globalId);

        return "Hello from Spring Boot!";
    }
}
