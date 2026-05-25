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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPayloadSanitizer {

    private final ObjectMapper objectMapper;

    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
            .build();

    /**
     * Validates the payload and, when possible, returns a rectified canonical
     * JSON string ready for deserialization.
     *
     * @param raw the {@code eventPayload} field value (may be {@code null})
     * @return the validated or rectified JSON string, or {@link Optional#empty()}
     *         when the payload is irrecoverable
     */
    public Optional<String> sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("[SANITIZER] eventPayload is null or blank - cannot deserialize");
            return Optional.empty();
        }

        String trimmed = raw.trim();
        Optional<String> strict = sanitizeStrictJson(raw, trimmed);
        if (strict.isPresent()) {
            return strict;
        }

        log.warn("[SANITIZER] eventPayload is not valid JSON - starting rectification attempts");
        Optional<String> unwrapped = unwrapDoubleSerialized(trimmed);
        if (unwrapped.isPresent()) {
            return unwrapped;
        }

        Optional<String> lenient = lenientReserialize(trimmed);
        if (lenient.isPresent()) {
            return lenient;
        }

        logIrrecoverable(trimmed);
        return Optional.empty();
    }

    private Optional<String> sanitizeStrictJson(String raw, String trimmed) {
        if (!isValidJson(trimmed)) {
            return Optional.empty();
        }

        logStrictJsonResult(raw, trimmed);
        return unwrapDoubleSerialized(trimmed).or(() -> Optional.of(trimmed));
    }

    private void logStrictJsonResult(String raw, String trimmed) {
        if (!trimmed.equals(raw)) {
            log.info("[SANITIZER] Rectified | strategy=TRIM_WHITESPACE | "
                    + "removed {} leading/trailing character(s)",
                    raw.length() - trimmed.length());
            return;
        }
        log.debug("[SANITIZER] eventPayload is valid JSON - no rectification needed");
    }

    private Optional<String> unwrapDoubleSerialized(String trimmed) {
        if (!trimmed.startsWith("\"")) {
            return Optional.empty();
        }
        try {
            String unwrapped = objectMapper.readValue(trimmed, String.class);
            if (!isValidJson(unwrapped)) {
                return Optional.empty();
            }
            log.info("[SANITIZER] Rectified | strategy=UNWRAP_DOUBLE_SERIALIZED | "
                    + "stripped one JSON string layer");
            return Optional.of(unwrapped);
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private Optional<String> lenientReserialize(String trimmed) {
        try {
            JsonNode node = LENIENT_MAPPER.readTree(trimmed);
            String canonical = objectMapper.writeValueAsString(node);
            log.info("[SANITIZER] Rectified | strategy=LENIENT_RESERIALIZE | "
                    + "accepted single-quotes / trailing-commas / unquoted-keys / comments");
            return Optional.of(canonical);
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private void logIrrecoverable(String trimmed) {
        String preview = trimmed.length() > 120
                ? trimmed.substring(0, 120) + "..."
                : trimmed;
        log.error("[SANITIZER] eventPayload is irrecoverable - message will be skipped. "
                + "Preview: {}", preview);
    }

    private boolean isValidJson(String s) {
        try {
            objectMapper.readTree(s);
            return true;
        } catch (Exception _) {
            return false;
        }
    }
}
