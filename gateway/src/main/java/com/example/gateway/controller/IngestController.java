package com.example.gateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private static final Logger logger = LoggerFactory.getLogger(IngestController.class);
    private static final String TOPIC_NAME = "ingest-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public IngestController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/events")
    public Mono<ResponseEntity<Void>> ingestEvent(@RequestBody Map<String, Object> eventData) {
        return Mono.fromCallable(() -> {
            logger.info("Received event in IngestController: {}", eventData);
            String eventJson = objectMapper.writeValueAsString(eventData);
            kafkaTemplate.send(TOPIC_NAME, eventJson);
            logger.info("Event sent to Kafka topic [{}]: {}", TOPIC_NAME, eventJson);
            return ResponseEntity.ok().<Void>build();
        }).onErrorResume(JsonProcessingException.class, e -> {
            logger.error("Error serializing event data to JSON", e);
            return Mono.just(ResponseEntity.badRequest().build());
        }).onErrorResume(Exception.class, e -> {
            logger.error("Error sending event to Kafka", e);
            return Mono.just(ResponseEntity.internalServerError().build());
        });
    }
}
