package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ValueConverterTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ValueConverter passthrough = new ValueConverter.Passthrough(json);
    private final ValueConverter booleanAsInt = new ValueConverter.BooleanAsInt();

    private ColumnMetadata col(String sqlType) {
        return ColumnMetadata.builder().sqlType(sqlType).build();
    }

    @Test
    void passthrough_parses_iso_timestamp_string_to_LocalDateTime() {
        Object out = passthrough.toJdbc("2024-11-15T09:30:00", col("TIMESTAMP"));
        assertEquals(LocalDateTime.of(2024, 11, 15, 9, 30, 0), out);
    }

    @Test
    void passthrough_parses_iso_date_string_to_LocalDate() {
        Object out = passthrough.toJdbc("1985-06-20", col("DATE"));
        assertEquals(LocalDate.of(1985, 6, 20), out);
    }

    @Test
    void passthrough_coerces_number_to_BigDecimal_for_NUMERIC() {
        // Compare by value (compareTo == 0) — BigDecimal("1500.00") and "1500.0" differ in scale
        BigDecimal out1 = (BigDecimal) passthrough.toJdbc(1500.00, col("NUMERIC"));
        assertEquals(0, new BigDecimal("1500.00").compareTo(out1));
        BigDecimal out2 = (BigDecimal) passthrough.toJdbc(42, col("NUMERIC"));
        assertEquals(0, new BigDecimal("42").compareTo(out2));
        // String input preserves declared scale
        BigDecimal out3 = (BigDecimal) passthrough.toJdbc("1500.00", col("NUMERIC"));
        assertEquals(new BigDecimal("1500.00"), out3);
    }

    @Test
    void passthrough_returns_null_when_input_is_null() {
        assertNull(passthrough.toJdbc(null, col("VARCHAR")));
        assertNull(passthrough.toJdbc(null, col("NUMERIC")));
        assertNull(passthrough.toJdbc(null, col("TIMESTAMP")));
    }

    @Test
    void booleanAsInt_round_trip() {
        ColumnMetadata c = col("NUMERIC");
        assertEquals(1, booleanAsInt.toJdbc(Boolean.TRUE, c));
        assertEquals(0, booleanAsInt.toJdbc(Boolean.FALSE, c));
        assertNull(booleanAsInt.toJdbc(null, c));

        assertEquals(Boolean.TRUE, booleanAsInt.fromJdbc(BigDecimal.ONE, c));
        assertEquals(Boolean.FALSE, booleanAsInt.fromJdbc(BigDecimal.ZERO, c));
        assertNull(booleanAsInt.fromJdbc(null, c));
    }

    @Test
    void booleanAsInt_accepts_string_inputs() {
        ColumnMetadata c = col("NUMERIC");
        assertEquals(1, booleanAsInt.toJdbc("true", c));
        assertEquals(0, booleanAsInt.toJdbc("false", c));
        assertEquals(1, booleanAsInt.toJdbc("1", c));
        assertEquals(0, booleanAsInt.toJdbc("0", c));
    }

    @Test
    void booleanAsInt_throws_on_unparseable_input() {
        assertThrows(IllegalArgumentException.class,
                () -> booleanAsInt.toJdbc("maybe", col("NUMERIC")));
    }
}
