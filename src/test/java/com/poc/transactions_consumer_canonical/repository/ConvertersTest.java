package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvertersTest {

    private final Converters converters = new Converters(new ObjectMapper());

    @Test
    void resolvesPassthroughByName() {
        assertThat(converters.forName("PASSTHROUGH")).isInstanceOf(ValueConverter.Passthrough.class);
        assertThat(converters.forName("passthrough")).isInstanceOf(ValueConverter.Passthrough.class);
    }

    @Test
    void resolvesBooleanAsIntByName() {
        assertThat(converters.forName("BOOLEAN_AS_INT")).isInstanceOf(ValueConverter.BooleanAsInt.class);
        assertThat(converters.forName("boolean_as_int")).isInstanceOf(ValueConverter.BooleanAsInt.class);
    }

    @Test
    void defaultsToPassthroughWhenNullOrBlank() {
        assertThat(converters.forName(null)).isInstanceOf(ValueConverter.Passthrough.class);
        assertThat(converters.forName("")).isInstanceOf(ValueConverter.Passthrough.class);
        assertThat(converters.forName("   ")).isInstanceOf(ValueConverter.Passthrough.class);
    }

    @Test
    void unknownConverterName_throws() {
        assertThatThrownBy(() -> converters.forName("WUT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown converter: WUT");
    }

    @Test
    void constructor_rejectsNullObjectMapper() {
        assertThatThrownBy(() -> new Converters(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jsonMapper");
    }
}
