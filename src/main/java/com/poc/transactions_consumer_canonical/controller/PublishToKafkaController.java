package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.producer.KafkaCanonicalProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publish")
@RequiredArgsConstructor
@Slf4j
public class PublishToKafkaController {

    private final KafkaCanonicalProducer kafkaProducer;

    @PostMapping
    public ResponseEntity<String> send(@Valid @RequestBody EventEnvelope envelope) {

        log.info("-----------------------------------------------");
        log.info("[REST API]  EventEnvelope received | eventId={} eventName={} source={}",
                envelope.getEventId(), envelope.getEventName(), envelope.getEventSource());

        kafkaProducer.send(envelope);

        log.info("[REST API]  Handed off to Producer");
        log.info("-----------------------------------------------");

        return ResponseEntity.accepted().body("EventEnvelope accepted | eventId=" + envelope.getEventId());
    }
}
