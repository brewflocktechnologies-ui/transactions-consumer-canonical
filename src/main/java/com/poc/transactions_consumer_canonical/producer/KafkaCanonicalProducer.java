package com.poc.transactions_consumer_canonical.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Step 2 — Serialises the EventEnvelope to JSON and sends it to the Kafka topic.
     */
    public void send(EventEnvelope envelope) {
        log.info("-----------------------------------------------");
        log.info("[PRODUCER]  Serialising EventEnvelope | eventId={} eventName={}",
                envelope.getEventId(), envelope.getEventName());

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("[PRODUCER]  Failed to serialise EventEnvelope to JSON", e);
            log.info("-----------------------------------------------");
            return;
        }

        kafkaTemplate.send(KafkaTopicConfig.TRANSACTIONS_TOPIC, envelope.getCorrelationId(), json)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[PRODUCER]  Sent successfully | topic={} partition={} offset={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("[PRODUCER]  Failed to send to Kafka", ex);
                    }
                    log.info("-----------------------------------------------");
                });
    }
}
