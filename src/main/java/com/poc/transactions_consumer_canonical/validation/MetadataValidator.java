package com.poc.transactions_consumer_canonical.validation;

import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs metadata-declared constraints against a payload {@link Map}.
 * Returns the field-error map (empty = valid). Caller decides whether to throw.
 */
@Component
public class MetadataValidator {

    public Map<String, String> validate(TableMetadata t, Map<String, Object> payload) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (payload == null) {
            errors.put("_body", "request body is required");
            return errors;
        }
        for (ColumnMetadata col : t.getColumns()) {
            validateColumn(col, payload, errors);
        }
        return errors;
    }

    private void validateColumn(ColumnMetadata col,
                                Map<String, Object> payload,
                                Map<String, String> errors) {
        if (col.isAudit() || col.isReadOnly()) return;
        String key = col.getJsonName();
        if (key == null) return;
        Object v = payload.get(key);

        if (col.isRequired() && v == null) {
            errors.put(key, "must not be null");
            return;
        }
        if (v instanceof String s) {
            validateStringConstraints(col, key, s, errors);
        }
    }

    private void validateStringConstraints(ColumnMetadata col,
                                           String key,
                                           String s,
                                           Map<String, String> errors) {
        if (col.getMaxLength() != null && s.length() > col.getMaxLength()) {
            errors.put(key, "must not exceed " + col.getMaxLength() + " characters");
        }
        if (col.getPattern() != null && !col.getPattern().isBlank()
                && !s.matches(col.getPattern())) {
            errors.put(key, "does not match required pattern");
        }
    }
}
