package com.poc.transactions_consumer_canonical.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Thrown by the metadata-driven validator when a payload fails one or more
 * declared constraints (required, maxLength, pattern). Carries a map of
 * jsonName → human-readable message so the global handler can build the same
 * 400 error shape as Jakarta {@code @Valid}.
 */
@Getter
public class MetadataValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> fieldErrors;

    public MetadataValidationException(Map<String, String> fieldErrors) {
        super("Metadata validation failed: " + fieldErrors);
        this.fieldErrors = fieldErrors;
    }
}
