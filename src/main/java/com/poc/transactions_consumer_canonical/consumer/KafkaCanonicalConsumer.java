package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingRegistry;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalRuleEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CaseInsensitiveJsonMap;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventPayloadSanitizer;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.service.ClearingEventService;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
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
 *  6. Deserialise eventPayload       →  Map&lt;String,Object&gt; (case-insensitive)
 *  7. CanonicalMappingEngine.map()   →  canonical Map&lt;String,Object&gt;
 *  8. Persist                        →  SendTransactionService.upsert / ClearingEventService
 * </pre>
 *
 * <p>The Kafka pipeline operates entirely on {@code Map<String,Object>} — there are
 * no intermediate typed source / request POJOs. Adding a new column or event type
 * is a YAML-only change.
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

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

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
        Optional<String> sanitized = payloadSanitizer.sanitize(envelope.getEventPayload());
        if (sanitized.isEmpty()) {
            log.warn("[CONSUMER]  eventPayload is invalid and cannot be rectified — skipping | "
                    + "eventType={} eventName={}", mapping.getEventType(), envelope.getEventName());
            log.info(OUTER_SEPARATOR);
            return;
        }
        envelope.setEventPayload(sanitized.get());

        // ── Step 6: deserialise payload as case-insensitive Map ──────────────
        Optional<Map<String, Object>> txnOpt = deserialisePayload(envelope);
        if (txnOpt.isEmpty()) return;
        Map<String, Object> txn = txnOpt.get();
        logTransaction(txn);

        // ── pipeline: CLRG_SETLMT branch ─────────────────────────────────────
        if (PIPELINE_CLRG_SETLMT.equalsIgnoreCase(mapping.getPipeline())) {
            handleClrgSetlmtEvent(mapping, txn, envelope);
            log.info(OUTER_SEPARATOR);
            return;
        }

        // ── Step 7: apply field mappings → canonical Map ─────────────────────
        String tranId = mappingEngine.extractTranId(mapping, txn, envelope);
        Map<String, Object> canonical = mappingEngine.map(mapping, txn, envelope);

        log.info("[CONSUMER]  Canonical map built | tranId={} tranType={} curStat={} tranAmt={}",
                tranId, canonical.get("tranType"), canonical.get("curStat"), canonical.get("tranAmt"));

        // ── Step 8: persist to Oracle DB ─────────────────────────────────────
        try {
            sendTransactionService.upsert(tranId, canonical);
            log.info("[CONSUMER]  Saved to DB successfully | tranId={} eventType={}",
                    tranId, mapping.getEventType());
        } catch (RuntimeException e) {
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
                                       Map<String, Object> txn,
                                       EventEnvelope envelope) {
        String eventType = mapping.getEventType();
        String tranId = mappingEngine.extractTranId(mapping, txn, envelope);
        Map<String, Object> payload = mappingEngine.applyTo(
                mapping.getClrgSetlmt(), txn, new LinkedHashMap<>());

        log.info("[CONSUMER]  {} payload built | tranId={} clrgSt={} setlAmt={}",
                eventType, tranId, payload.get("clrgSt"), payload.get("setlAmt"));

        try {
            if (SETTLEMENT_EVENT_TYPE.equalsIgnoreCase(eventType)) {
                clearingEventService.upsertSettlement(tranId, payload);
            } else {
                clearingEventService.upsertClearing(tranId, payload);
            }
            log.info("[CONSUMER]  {} processing complete | tranId={}", eventType, tranId);
        } catch (RuntimeException e) {
            log.error("[CONSUMER]  {} upsert failed | tranId={} error={}",
                    eventType, tranId, e.getMessage(), e);
        }
    }

    private Optional<Map<String, Object>> deserialisePayload(EventEnvelope envelope) {
        try {
            Map<String, Object> raw = objectMapper.readValue(envelope.getEventPayload(), MAP_TYPE);
            return Optional.of(CaseInsensitiveJsonMap.wrapMap(raw));
        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to deserialise eventPayload as JSON object: {}", e.getMessage());
            log.info(OUTER_SEPARATOR);
            return Optional.empty();
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

    /**
     * Logs a short summary of the deserialised payload. Field names listed below
     * are common across known producers — missing keys simply log as {@code null},
     * matching the case-insensitive lookup behaviour used downstream.
     */
    private void logTransaction(Map<String, Object> txn) {
        log.info(INNER_SEPARATOR);
        log.info("[CONSUMER]  eventPayload (deserialised)");
        log.info("  tranId              : {}", txn.get("tranId"));
        log.info("  tranAmt             : {}", txn.get("tranAmt"));
        log.info("  tranAmtCurr         : {}", txn.get("tranAmtCurr"));
        log.info("  status              : {}", txn.get("status"));
        log.info("  fundingStatus       : {}", txn.get("fundingStatus"));
        log.info("  fundingId           : {}", txn.get("fundingId"));
        log.info("  networkCode         : {}", txn.get("networkCode"));
        log.info("  fundingNetworkCode  : {}", txn.get("fundingNetworkCode"));
        log.info("  sndrFirstName       : {}", txn.get("sndrFirstName"));
        log.info("  sndrLastName        : {}", txn.get("sndrLastName"));
        log.info("  rcvrFirstName       : {}", txn.get("rcvrFirstName"));
        log.info("  rcvrLastName        : {}", txn.get("rcvrLastName"));
        log.info("  originatingInstId   : {}", txn.get("originatingInstId"));
        log.info("  channel             : {}", txn.get("channel"));
    }

}
