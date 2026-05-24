package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One field-level rule: read {@code source} from {@link com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage}
 * and write to {@code target} on the canonical DTO.
 *
 * <p>{@code converter} is optional.  When absent the engine auto-coerces the
 * value based on the setter's parameter type (String→BigDecimal, String→LocalDate …).
 * Supported explicit converter values:
 * <ul>
 *   <li>{@code STRING_TO_DECIMAL}  — String → BigDecimal</li>
 *   <li>{@code STRING_TO_DATE}     — String (yyyy-MM-dd or ISO) → LocalDate</li>
 *   <li>{@code STRING_TO_DATETIME} — String (ISO) → LocalDateTime</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMapping {

    /** Getter-reachable field name on {@code TransactionEventAxonMessage}. */
    private String source;

    /** Setter-reachable field name on the target canonical DTO. */
    private String target;

    /** Optional converter hint.  Auto-coercion is used when absent. */
    private String converter;
}
