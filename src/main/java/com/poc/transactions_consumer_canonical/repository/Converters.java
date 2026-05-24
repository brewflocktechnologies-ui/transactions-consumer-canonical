package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Locale;

/**
 * Resolves {@link ValueConverter} instances by name. The set of converters is
 * fixed at boot — adding a new converter is a code change (acceptable because
 * column-level config still drives which one is used per column).
 */
@Component
public final class Converters {

    private final ValueConverter passthrough;
    private final ValueConverter booleanAsInt;

    public Converters(ObjectMapper jsonMapper) {
        Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.passthrough = new ValueConverter.Passthrough(jsonMapper);
        this.booleanAsInt = new ValueConverter.BooleanAsInt();
    }

    public ValueConverter forName(String name) {
        String n = (name == null || name.isBlank()) ? "PASSTHROUGH" : name.toUpperCase(Locale.ROOT);
        return switch (n) {
            case "PASSTHROUGH"    -> passthrough;
            case "BOOLEAN_AS_INT" -> booleanAsInt;
            default -> throw new IllegalArgumentException("Unknown converter: " + n);
        };
    }
}
