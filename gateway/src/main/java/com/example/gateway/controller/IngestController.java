package com.example.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private static final Logger logger = LoggerFactory.getLogger(IngestController.class);

    @PostMapping("/events")
    public ResponseEntity<Void> ingestEvent(@RequestBody Map<String, Object> eventData) {
        logger.info("Received event: {}", eventData);
        return ResponseEntity.ok().build();
    }
}
