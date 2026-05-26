package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Evaluates per-event-type filtering rules declared in YAML before a Kafka message
 * is forwarded to the canonical mapping engine.
 *
 * <h3>Supported rules</h3>
 * <ol>
 *   <li><b>allowedEventSources</b> — whitelist checked against
 *       {@code EventEnvelope.eventSource}.</li>
 *   <li><b>allowedOperations</b> — whitelist checked against the {@code operation}
 *       field parsed from the {@code EventEnvelope.eventMetadata} JSON string.</li>
 * </ol>
 *
 * <p>An empty / absent list means "allow everything" for that dimension.
 * A message is processed only when <em>all</em> configured rules pass.
 *
 * <h3>Evaluation order</h3>
 * <ol>
 *   <li>If no {@code rules:} block is present → allow (fast path).</li>
 *   <li>eventSource check.</li>
 *   <li>eventMetadata.operation check.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanonicalRuleEngine {

    private final ObjectMapper objectMapper;   // shared Jackson JSON mapper (Spring bean)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the message satisfies all configured rules and should
     * be forwarded to the mapping pipeline; {@code false} if it should be skipped.
     *
     * @param mapping  the event-type mapping that owns the rules
     * @param envelope the inbound Kafka event envelope
     */
    public boolean shouldProcess(EventTypeMapping mapping, EventEnvelope envelope) {

        RulesConfig rules = mapping.getRules();

        if (rules == null) {
            log.debug("[RULE ENGINE] No rules block for eventType={} — allowing message",
                    mapping.getEventType());
            return true;
        }

        // ── Rule 1: eventSource whitelist ─────────────────────────────────────
        if (!isAllowed("eventSource",
                        envelope.getEventSource(),
                        rules.getAllowedEventSources())) {
            log.info("[RULE ENGINE] IGNORED | rule=eventSource | value='{}' | "
                            + "allowed={} | eventType={} | eventName={}",
                    envelope.getEventSource(),
                    rules.getAllowedEventSources(),
                    mapping.getEventType(),
                    envelope.getEventName());
            return false;
        }

        // ── Rule 2: eventMetadata.operation whitelist ─────────────────────────
        String operation = parseOperation(envelope.getEventMetadata());
        if (!isAllowed("operation",
                        operation,
                        rules.getAllowedOperations())) {
            log.info("[RULE ENGINE] IGNORED | rule=operation | value='{}' | "
                            + "allowed={} | eventType={} | eventName={}",
                    operation,
                    rules.getAllowedOperations(),
                    mapping.getEventType(),
                    envelope.getEventName());
            return false;
        }

        log.debug("[RULE ENGINE] ALLOWED | eventSource='{}' | operation='{}' | eventType={} | eventName={}",
                envelope.getEventSource(), operation,
                mapping.getEventType(), envelope.getEventName());
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Checks whether {@code value} appears in {@code allowedList} (case-insensitive).
     * Returns {@code true} unconditionally when {@code allowedList} is null or empty.
     */
    private boolean isAllowed(String dimension, String value, List<String> allowedList) {
        if (allowedList == null || allowedList.isEmpty()) {
            return true;   // no restriction configured
        }
        if (value == null || value.isBlank()) {
            log.warn("[RULE ENGINE] {} value is blank but allowedList={} — denying",
                    dimension, allowedList);
            return false;
        }
        return allowedList.stream()
                          .anyMatch(allowed -> allowed.equalsIgnoreCase(value.trim()));
    }

    /**
     * Extracts the {@code operation} field from the {@code eventMetadata} JSON string.
     *
     * <p>Example input: {@code "{\"operation\":\"A\",\"timestamp\":\"2025-02-04T12:30:00Z\"}"}
     *
     * @return the operation string, or {@code null} if absent/unparseable
     */
    private String parseOperation(String eventMetadata) {
        if (eventMetadata == null || eventMetadata.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(eventMetadata);
            JsonNode opNode = root.get("operation");
            return (opNode != null && !opNode.isNull()) ? opNode.asText() : null;
        } catch (IOException e) {
            log.warn("[RULE ENGINE] Cannot parse eventMetadata JSON: '{}' — {}",
                    eventMetadata, e.getMessage());
            return null;
        }
    }
}
