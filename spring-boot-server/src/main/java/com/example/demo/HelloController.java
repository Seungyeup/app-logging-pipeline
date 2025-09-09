package com.example.demo;

import com.example.demo.entity.LogEntry;
import com.example.demo.repository.LogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC; // Import MDC
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime; // Import LocalDateTime

@RestController
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @Autowired // Inject LogEntryRepository
    private LogEntryRepository logEntryRepository;

    @GetMapping("/api/hello")
    public String hello() {
        log.info("Hello API called.");

        // Get globalId from MDC
        String globalId = MDC.get("globalId");
        if (globalId == null) {
            globalId = "unknown"; // Fallback if globalId is not set (should not happen with filter)
        }

        // Create and save LogEntry
        LogEntry logEntry = new LogEntry(
                globalId,
                "Hello API call received", // Message for the log entry
                LocalDateTime.now(),
                "INFO" // Log level
        );
        logEntryRepository.save(logEntry);
        log.info("LogEntry saved to DB with globalId: {}", globalId); // Log the save action

        return "Hello from Spring Boot!";
    }
}
