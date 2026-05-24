package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalConsumer {

    private final ObjectMapper objectMapper;

    /**
     * Step 3 — Receives a JSON-serialised EventEnvelope from Kafka.
     * Step 4 — Deserialises the envelope, then deserialises eventPayload
     *           into TransactionEventAxonMessage and logs both.
     */
    @KafkaListener(
            topics  = KafkaTopicConfig.TRANSACTIONS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {

        log.info("===============================================");
        log.info("[CONSUMER]  Raw message received from Kafka");

        // ── Step 1: deserialise outer envelope ──────────────────────────
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise EventEnvelope - raw message: {}", message, e);
            log.info("===============================================");
            return;
        }

        log.info("-----------------------------------------------");
        log.info("[CONSUMER]  EventEnvelope");
        log.info("  eventId          : {}", envelope.getEventId());
        log.info("  eventName        : {}", envelope.getEventName());
        log.info("  eventSource      : {}", envelope.getEventSource());
        log.info("  correlationId    : {}", envelope.getCorrelationId());
        log.info("  regulatoryRegion : {}", envelope.getRegulatoryRegion());
        log.info("  eventTimestamp   : {}", envelope.getEventTimestamp());
        log.info("  eventMetadata    : {}", envelope.getEventMetadata());
        log.info("  ignore           : {}", envelope.isIgnore());

        if (envelope.isIgnore()) {
            log.info("[CONSUMER]  Envelope flagged ignore=true — skipping payload processing");
            log.info("===============================================");
            return;
        }

        // ── Step 2: deserialise inner payload ───────────────────────────
        String rawPayload = envelope.getEventPayload();
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("[CONSUMER]  eventPayload is empty — nothing to process");
            log.info("===============================================");
            return;
        }

        TransactionEventAxonMessage txn;
        try {
            txn = objectMapper.readValue(rawPayload, TransactionEventAxonMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise TransactionEventAxonMessage from eventPayload: {}",
                    rawPayload, e);
            log.info("===============================================");
            return;
        }

        log.info("-----------------------------------------------");
        log.info("[CONSUMER]  TransactionEventAxonMessage");
        log.info("  tranId              : {}", txn.getTranId());
        log.info("  tranAmt             : {}", txn.getTranAmt());
        log.info("  tranAmtCurr         : {}", txn.getTranAmtCurr());
        log.info("  status              : {}", txn.getStatus());
        log.info("  networkCode         : {}", txn.getNetworkCode());
        log.info("  correlationId       : {}", txn.getCorrelationId());
        log.info("  sndrFirstName       : {}", txn.getSndrFirstName());
        log.info("  sndrLastName        : {}", txn.getSndrLastName());
        log.info("  rcvrFirstName       : {}", txn.getRcvrFirstName());
        log.info("  rcvrLastName        : {}", txn.getRcvrLastName());
        log.info("  originatingInstId   : {}", txn.getOriginatingInstId());
        log.info("  originatingInstName : {}", txn.getOriginatingInstName());
        log.info("  paymentStatus       : {}", txn.getPaymentStatus());
        log.info("  fundingStatus       : {}", txn.getFundingStatus());
        log.info("  channel             : {}", txn.getChannel());
        log.info("  brand               : {}", txn.getBrand());
        log.info("===============================================");
    }
}
