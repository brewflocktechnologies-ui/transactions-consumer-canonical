package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Filtering rules declared per event-type YAML under the {@code rules:} key.
 *
 * <pre>
 * rules:
 *   allowedEventSources:
 *     - AIS_SERVICE
 *     - PAYMENT_HUB
 *   allowedOperations:
 *     - A
 *     - U
 * </pre>
 *
 * <p>An <b>empty or absent list</b> means "allow all values" for that dimension.
 * Both rules must pass for a message to be forwarded to the mapping engine.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RulesConfig {

    /**
     * Whitelist of {@code EventEnvelope.eventSource} values that should be processed.
     * <br>Null / empty → no restriction (all sources are allowed).
     */
    private List<String> allowedEventSources;

    /**
     * Whitelist of {@code operation} values extracted from the
     * {@code EventEnvelope.eventMetadata} JSON string
     * (e.g. {@code {"operation":"A","timestamp":"2025-02-04T12:30:00Z"}}).
     * <br>Null / empty → no restriction (all operations are allowed).
     */
    private List<String> allowedOperations;
}
