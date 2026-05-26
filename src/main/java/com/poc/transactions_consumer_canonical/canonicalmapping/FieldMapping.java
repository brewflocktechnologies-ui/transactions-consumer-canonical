package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One field-level mapping rule: read {@code source} from the incoming JSON payload
 * (as a case-insensitive {@code Map<String,Object>}) and write the value under
 * {@code target} on the canonical payload {@code Map}.
 *
 * <p>The {@code source} path supports dot notation for nested JSON objects
 * (e.g. {@code "sendingAccountEligible.eligible"} resolves
 * {@code payload.get("sendingAccountEligible").get("eligible")}).
 * The {@code target} corresponds to the {@code jsonName} of a column in
 * {@code metadata/*.yaml} — the persistence layer ({@code GenericTableRepository})
 * looks up the column by that name and applies the {@code converter}/{@code sqlType}
 * defined in the metadata at JDBC bind time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMapping {

    /** Dot-notation path into the source JSON payload (case-insensitive lookup). */
    private String source;

    /** Target key in the canonical payload — matches a {@code jsonName} from {@code metadata/*.yaml}. */
    private String target;

    /**
     * Optional converter hint, retained for backwards-compatible YAML parsing.
     * Type coercion is performed at JDBC bind time by
     * {@link com.poc.transactions_consumer_canonical.repository.ValueConverter},
     * driven off the column's {@code sqlType} and {@code converter} from
     * {@code metadata/*.yaml}; this field is not consulted by the runtime engine.
     */
    private String converter;
}
