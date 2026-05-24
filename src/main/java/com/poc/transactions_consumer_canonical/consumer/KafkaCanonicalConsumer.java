package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingRegistry;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalRuleEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka consumer that orchestrates the canonical mapping pipeline:
 *
 * <pre>
 *  1. Deserialise raw JSON        →  EventEnvelope
 *  2. Check envelope.ignore flag     (set upstream; skip if true)
 *  3. Route by eventName             →  EventTypeMapping  (YAML-driven)
 *  4. Evaluate rules                 →  allowedEventSources / allowedOperations
 *  5. Deserialise eventPayload       →  TransactionEventAxonMessage
 *  6. CanonicalMappingEngine.map()   →  SendTransactionRequest
 *  7. SendTransactionService.upsert()→  Oracle DB
 * </pre>
 *
 * <p>Rule evaluation (step 4) is intentionally placed <em>before</em> payload
 * deserialisation (step 5) so that filtered-out messages never incur the cost
 * of parsing the larger {@code eventPayload} JSON.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalConsumer {

    private final ObjectMapper              objectMapper;
    private final CanonicalMappingRegistry  mappingRegistry;
    private final CanonicalRuleEngine       ruleEngine;
    private final CanonicalMappingEngine    mappingEngine;
    private final SendTransactionService    sendTransactionService;

    @KafkaListener(
            topics  = KafkaTopicConfig.TRANSACTIONS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        log.info("===============================================");
        log.info("[CONSUMER]  Message received from Kafka");

        // ── Step 1: deserialise EventEnvelope ───────────────────────────────
        EventEnvelope envelope = deserialiseEnvelope(message);
        if (envelope == null) return;

        logEnvelope(envelope);

        // ── Step 2: check ignore flag (set upstream) ─────────────────────────
        if (envelope.isIgnore()) {
            log.info("[CONSUMER]  ignore=true — skipping (flagged upstream)");
            log.info("===============================================");
            return;
        }

        // ── Step 3: route eventName → EventTypeMapping ───────────────────────
        Optional<EventTypeMapping> mappingOpt =
                mappingRegistry.findByEventName(envelope.getEventName());

        if (mappingOpt.isEmpty()) {
            log.warn("[CONSUMER]  No canonical mapping for eventName='{}' — skipping. "
                    + "Add a YAML entry under canonical-mappings/ to handle this event.",
                    envelope.getEventName());
            log.info("===============================================");
            return;
        }

        EventTypeMapping mapping = mappingOpt.get();
        log.info("[CONSUMER]  Matched mapping | eventType={}", mapping.getEventType());

        // ── Step 4: evaluate rules ───────────────────────────────────────────
        if (!ruleEngine.shouldProcess(mapping, envelope)) {
            log.info("[CONSUMER]  Message blocked by rule engine | "
                    + "eventType={} eventName={} eventSource={}",
                    mapping.getEventType(), envelope.getEventName(), envelope.getEventSource());
            log.info("===============================================");
            return;
        }

        // ── Step 5: deserialise TransactionEventAxonMessage (payload) ─────────
        TransactionEventAxonMessage txn = deserialisePayload(envelope);
        if (txn == null) return;

        logTransaction(txn);

        // ── Step 6: apply field mappings → canonical SendTransactionRequest ───
        String tranId = mappingEngine.extractTranId(mapping, txn, envelope);
        SendTransactionRequest canonicalReq = mappingEngine.map(mapping, txn, envelope);

        log.info("[CONSUMER]  Canonical request built | tranId={} tranType={} curStat={} tranAmt={}",
                tranId, canonicalReq.getTranType(), canonicalReq.getCurStat(), canonicalReq.getTranAmt());

        // ── Step 7: persist to Oracle DB ─────────────────────────────────────
        try {
            sendTransactionService.upsert(tranId, canonicalReq);
            log.info("[CONSUMER]  Saved to DB successfully | tranId={} eventType={}",
                    tranId, mapping.getEventType());
        } catch (Exception e) {
            log.error("[CONSUMER]  DB upsert failed | tranId={} eventType={} error={}",
                    tranId, mapping.getEventType(), e.getMessage(), e);
        }

        log.info("===============================================");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EventEnvelope deserialiseEnvelope(String raw) {
        try {
            return objectMapper.readValue(raw, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise EventEnvelope: {}", e.getMessage());
            log.info("===============================================");
            return null;
        }
    }

    private TransactionEventAxonMessage deserialisePayload(EventEnvelope envelope) {
        String rawPayload = envelope.getEventPayload();
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("[CONSUMER]  eventPayload is empty — nothing to process");
            log.info("===============================================");
            return null;
        }
        try {
            return objectMapper.readValue(rawPayload, TransactionEventAxonMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise TransactionEventAxonMessage: {}",
                    e.getMessage());
            log.info("===============================================");
            return null;
        }
    }

    private void logEnvelope(EventEnvelope e) {
        log.info("-----------------------------------------------");
        log.info("[CONSUMER]  EventEnvelope");
        log.info("  eventId          : {}", e.getEventId());
        log.info("  eventName        : {}", e.getEventName());
        log.info("  eventSource      : {}", e.getEventSource());
        log.info("  correlationId    : {}", e.getCorrelationId());
        log.info("  regulatoryRegion : {}", e.getRegulatoryRegion());
        log.info("  eventTimestamp   : {}", e.getEventTimestamp());
        log.info("  eventMetadata    : {}", e.getEventMetadata());
        log.info("  ignore           : {}", e.isIgnore());
    }

    private void logTransaction(TransactionEventAxonMessage txn) {
        log.info("-----------------------------------------------");
        log.info("[CONSUMER]  TransactionEventAxonMessage");
        log.info("  tranId              : {}", txn.getTranId());
        log.info("  tranAmt             : {}", txn.getTranAmt());
        log.info("  tranAmtCurr         : {}", txn.getTranAmtCurr());
        log.info("  status              : {}", txn.getStatus());
        log.info("  fundingStatus       : {}", txn.getFundingStatus());
        log.info("  fundingId           : {}", txn.getFundingId());
        log.info("  networkCode         : {}", txn.getNetworkCode());
        log.info("  fundingNetworkCode  : {}", txn.getFundingNetworkCode());
        log.info("  sndrFirstName       : {}", txn.getSndrFirstName());
        log.info("  sndrLastName        : {}", txn.getSndrLastName());
        log.info("  rcvrFirstName       : {}", txn.getRcvrFirstName());
        log.info("  rcvrLastName        : {}", txn.getRcvrLastName());
        log.info("  originatingInstId   : {}", txn.getOriginatingInstId());
        log.info("  channel             : {}", txn.getChannel());
    }
}
