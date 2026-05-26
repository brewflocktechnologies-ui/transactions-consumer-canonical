package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingRegistry;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalRuleEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventPayloadSanitizer;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.dto.SendTranClrgSetlmtRequest;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import com.poc.transactions_consumer_canonical.service.ClearingEventService;
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
 *  5. Validate &amp; sanitize payload   →  rectify common JSON issues or skip
 *  6. Deserialise eventPayload       →  TransactionEventAxonMessage
 *  7. CanonicalMappingEngine.map()   →  SendTransactionRequest
 *  8. SendTransactionService.upsert()→  Oracle DB
 * </pre>
 *
 * <p>Steps 3–5 all run <em>before</em> full payload deserialization so that
 * unroutable, rule-blocked, or malformed messages are discarded with minimal cost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalConsumer {

    private static final String OUTER_SEPARATOR = "===============================================";
    private static final String INNER_SEPARATOR = "-----------------------------------------------";

    private static final String PIPELINE_CLRG_SETLMT  = "CLRG_SETLMT";
    private static final String SETTLEMENT_EVENT_TYPE = "SETTLEMENT";

    private final ObjectMapper              objectMapper;
    private final CanonicalMappingRegistry  mappingRegistry;
    private final CanonicalRuleEngine       ruleEngine;
    private final EventPayloadSanitizer     payloadSanitizer;
    private final CanonicalMappingEngine    mappingEngine;
    private final SendTransactionService    sendTransactionService;
    private final ClearingEventService      clearingEventService;

    @KafkaListener(
            topics  = KafkaTopicConfig.TRANSACTIONS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        log.info(OUTER_SEPARATOR);
        log.info("[CONSUMER]  Message received from Kafka");

        // ── Step 1: deserialise EventEnvelope ───────────────────────────────
        EventEnvelope envelope = deserialiseEnvelope(message);
        if (envelope == null) return;

        logEnvelope(envelope);

        // ── Step 2: check ignore flag (set upstream) ─────────────────────────
        if (envelope.isIgnore()) {
            log.info("[CONSUMER]  ignore=true — skipping (flagged upstream)");
            log.info(OUTER_SEPARATOR);
            return;
        }

        // ── Step 3: route eventName → EventTypeMapping ───────────────────────
        Optional<EventTypeMapping> mappingOpt =
                mappingRegistry.findByEventName(envelope.getEventName());

        if (mappingOpt.isEmpty()) {
            log.warn("[CONSUMER]  No canonical mapping for eventName='{}' — skipping. "
                    + "Add a YAML entry under canonical-mappings/ to handle this event.",
                    envelope.getEventName());
            log.info(OUTER_SEPARATOR);
            return;
        }

        EventTypeMapping mapping = mappingOpt.get();
        log.info("[CONSUMER]  Matched mapping | eventType={}", mapping.getEventType());

        // ── Step 4: evaluate rules ───────────────────────────────────────────
        if (!ruleEngine.shouldProcess(mapping, envelope)) {
            log.info("[CONSUMER]  Message blocked by rule engine | "
                    + "eventType={} eventName={} eventSource={}",
                    mapping.getEventType(), envelope.getEventName(), envelope.getEventSource());
            log.info(OUTER_SEPARATOR);
            return;
        }

        // ── Step 5: validate & sanitize eventPayload ─────────────────────────
        // Attempts four progressive rectification strategies (trim → unwrap →
        // lenient re-parse). Returns empty if the payload is irrecoverable.
        Optional<String> sanitized = payloadSanitizer.sanitize(envelope.getEventPayload());
        if (sanitized.isEmpty()) {
            log.warn("[CONSUMER]  eventPayload is invalid and cannot be rectified — skipping | "
                    + "eventType={} eventName={}", mapping.getEventType(), envelope.getEventName());
            log.info(OUTER_SEPARATOR);
            return;
        }
        // Apply the (potentially rectified) payload back — no-op when unchanged
        envelope.setEventPayload(sanitized.get());

        // ── Step 6: deserialise TransactionEventAxonMessage (payload) ─────────
        TransactionEventAxonMessage txn = deserialisePayload(envelope);
        if (txn == null) return;

        logTransaction(txn);

        // ── pipeline: CLRG_SETLMT branch (dual-message 2nd/3rd leg) ─────────
        // Any event whose YAML declares `pipeline: CLRG_SETLMT` bypasses the
        // standard SendTransactionRequest path. Adding a new 5th-table event
        // type only requires a new YAML file — no Java change needed here.
        if (PIPELINE_CLRG_SETLMT.equalsIgnoreCase(mapping.getPipeline())) {
            handleClrgSetlmtEvent(mapping, txn, envelope);
            log.info(OUTER_SEPARATOR);
            return;
        }

        // ── Step 7: apply field mappings → canonical SendTransactionRequest ───
        String tranId = mappingEngine.extractTranId(mapping, txn, envelope);
        SendTransactionRequest canonicalReq = mappingEngine.map(mapping, txn, envelope);

        log.info("[CONSUMER]  Canonical request built | tranId={} tranType={} curStat={} tranAmt={}",
                tranId, canonicalReq.getTranType(), canonicalReq.getCurStat(), canonicalReq.getTranAmt());

        // ── Step 8: persist to Oracle DB ─────────────────────────────────────
        try {
            sendTransactionService.upsert(tranId, canonicalReq);
            log.info("[CONSUMER]  Saved to DB successfully | tranId={} eventType={}",
                    tranId, mapping.getEventType());
        } catch (Exception e) {
            log.error("[CONSUMER]  DB upsert failed | tranId={} eventType={} error={}",
                    tranId, mapping.getEventType(), e.getMessage(), e);
        }

        log.info(OUTER_SEPARATOR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EventEnvelope deserialiseEnvelope(String raw) {
        try {
            return objectMapper.readValue(raw, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise EventEnvelope: {}", e.getMessage());
            log.info(OUTER_SEPARATOR);
            return null;
        }
    }

    private void handleClrgSetlmtEvent(EventTypeMapping mapping,
                                       TransactionEventAxonMessage txn,
                                       EventEnvelope envelope) {
        String eventType = mapping.getEventType();
        String tranId = mappingEngine.extractTranId(mapping, txn, envelope);
        SendTranClrgSetlmtRequest req = mappingEngine.applyTo(
                mapping.getClrgSetlmt(), txn, new SendTranClrgSetlmtRequest());
        req.setTranId(tranId);

        log.info("[CONSUMER]  {} request built | tranId={} clrgSt={} setlAmt={}",
                eventType, tranId, req.getClrgSt(), req.getSetlAmt());

        try {
            if (SETTLEMENT_EVENT_TYPE.equalsIgnoreCase(eventType)) {
                clearingEventService.upsertSettlement(req);
            } else {
                clearingEventService.upsertClearing(req);
            }
            log.info("[CONSUMER]  {} processing complete | tranId={}", eventType, tranId);
        } catch (Exception e) {
            log.error("[CONSUMER]  {} upsert failed | tranId={} error={}",
                    eventType, tranId, e.getMessage(), e);
        }
    }

    private TransactionEventAxonMessage deserialisePayload(EventEnvelope envelope) {
        try {
            return objectMapper.readValue(envelope.getEventPayload(),
                    TransactionEventAxonMessage.class);
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise TransactionEventAxonMessage: {}",
                    e.getMessage());
            log.info(OUTER_SEPARATOR);
            return null;
        }
    }

    private void logEnvelope(EventEnvelope e) {
        log.info(INNER_SEPARATOR);
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
        log.info(INNER_SEPARATOR);
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
