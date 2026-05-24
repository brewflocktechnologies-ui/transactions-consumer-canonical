package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates and auto-rectifies the {@code eventPayload} JSON string carried by an
 * {@link com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope} before
 * it is deserialized into a domain object.
 *
 * <h3>Rectification strategy (attempts in order)</h3>
 * <ol>
 *   <li><b>Valid as-is</b> — fast path; payload passes through unchanged.</li>
 *   <li><b>Trim whitespace</b> — strips leading / trailing whitespace and
 *       re-validates; the most common source of cosmetic invalidity.</li>
 *   <li><b>Unwrap double-serialized JSON</b> — handles payloads that were
 *       accidentally serialized twice (i.e. a JSON string whose content is itself
 *       valid JSON). The outer string layer is stripped and the inner JSON is
 *       used as the canonical payload.</li>
 *   <li><b>Lenient re-serialization</b> — parses with relaxed Jackson rules
 *       (single quotes, trailing commas, unquoted field names, JavaScript
 *       comments) and re-emits strict RFC-8259 JSON.  Handles hand-crafted or
 *       legacy payloads with minor syntax deviations.</li>
 * </ol>
 *
 * <p>If all four attempts fail the payload is considered <em>irrecoverable</em>
 * and {@link Optional#empty()} is returned, causing the consumer to skip the
 * message without a DB write.
 *
 * <h3>Thread safety</h3>
 * Both the shared {@code ObjectMapper} (injected) and the static
 * {@code LENIENT_MAPPER} are fully thread-safe after construction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPayloadSanitizer {

    /** Strict Jackson mapper — the shared Spring bean from application context. */
    private final ObjectMapper objectMapper;

    /**
     * Lenient Jackson mapper built once at class-load time.
     * Accepts common JSON relaxations; outputs strict RFC-8259 JSON when writing.
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)          // // and /* */ comments
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)          // 'value' instead of "value"
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)   // {key: "value"}
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)         // [1, 2, 3,]
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES)         // [1,,3] sparse arrays
            .build();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates the payload and, when possible, returns a rectified canonical
     * JSON string ready for deserialization.
     *
     * @param raw the {@code eventPayload} field value (may be {@code null})
     * @return the validated / rectified JSON string, or {@link Optional#empty()}
     *         when the payload is irrecoverable
     */
    public Optional<String> sanitize(String raw) {

        // ── Guard ────────────────────────────────────────────────────────────
        if (raw == null || raw.isBlank()) {
            log.warn("[SANITIZER] eventPayload is null or blank — cannot deserialize");
            return Optional.empty();
        }

        // ── Attempt 1: valid as-is ───────────────────────────────────────────
        if (isValidJson(raw)) {
            log.debug("[SANITIZER] eventPayload is valid JSON — no rectification needed");
            return Optional.of(raw);
        }

        log.warn("[SANITIZER] eventPayload is not valid JSON — starting rectification attempts");

        // ── Attempt 2: trim whitespace ───────────────────────────────────────
        String trimmed = raw.trim();
        if (!trimmed.equals(raw) && isValidJson(trimmed)) {
            log.info("[SANITIZER] Rectified ✓ | strategy=TRIM_WHITESPACE | "
                    + "removed {} leading/trailing character(s)",
                    raw.length() - trimmed.length());
            return Optional.of(trimmed);
        }

        // ── Attempt 3: unwrap double-serialized JSON ─────────────────────────
        // Scenario: producer called objectMapper.writeValueAsString() twice,
        // yielding a JSON-encoded string whose unescaped content is the real JSON.
        // e.g.  "\"{ \\\"tranId\\\": \\\"TXN-001\\\" }\""
        if (trimmed.startsWith("\"")) {
            try {
                String unwrapped = objectMapper.readValue(trimmed, String.class);
                if (isValidJson(unwrapped)) {
                    log.info("[SANITIZER] Rectified ✓ | strategy=UNWRAP_DOUBLE_SERIALIZED | "
                            + "stripped one JSON string layer");
                    return Optional.of(unwrapped);
                }
            } catch (Exception ignored) {
                // trimmed value is not a valid JSON string — fall through
            }
        }

        // ── Attempt 4: lenient re-serialization ─────────────────────────────
        // Parse with relaxed rules, then re-emit strict canonical JSON so the
        // downstream deserializer always sees RFC-8259-compliant input.
        try {
            JsonNode node = LENIENT_MAPPER.readTree(trimmed);
            String canonical = objectMapper.writeValueAsString(node);
            log.info("[SANITIZER] Rectified ✓ | strategy=LENIENT_RESERIALIZE | "
                    + "accepted single-quotes / trailing-commas / unquoted-keys / comments");
            return Optional.of(canonical);
        } catch (Exception ignored) {
            // truly irrecoverable
        }

        // ── All attempts exhausted ───────────────────────────────────────────
        String preview = trimmed.length() > 120
                ? trimmed.substring(0, 120) + "…"
                : trimmed;
        log.error("[SANITIZER] eventPayload is irrecoverable — message will be skipped. "
                + "Preview: {}", preview);
        return Optional.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns {@code true} only when {@code s} can be parsed as strict JSON. */
    private boolean isValidJson(String s) {
        try {
            objectMapper.readTree(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
