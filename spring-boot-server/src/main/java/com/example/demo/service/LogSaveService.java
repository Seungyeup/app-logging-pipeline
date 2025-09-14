package com.example.demo.service;

import com.example.demo.entity.LogEntry;
import com.example.demo.repository.LogEntryRepository;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LogSaveService {

    private static final Logger log = LoggerFactory.getLogger(LogSaveService.class);

    private final LogEntryRepository logEntryRepository;

    @Autowired
    public LogSaveService(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    // Micrometer는 스팬 이름으로 contextualName을 우선 사용
    @Observed(name = "save-log-to-db", contextualName = "save-log-to-db")
    public void saveLog(LogEntry logEntry) {
        log.info("Inside LogSaveService.saveLog. Attempting to save log to DB.");
        logEntryRepository.save(logEntry);
    }
}
