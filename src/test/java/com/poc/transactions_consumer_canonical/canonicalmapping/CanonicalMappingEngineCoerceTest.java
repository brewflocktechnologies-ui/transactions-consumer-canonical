package com.poc.transactions_consumer_canonical.canonicalmapping;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the private coerce(Object, Class) helper directly via reflection
 * to cover every primitive/wrapper target-type branch — including the ones not
 * reachable from the runtime DTOs (Integer, primitive boolean/long, etc.).
 */
class CanonicalMappingEngineCoerceTest {

    private static Object coerce(Object value, Class<?> target) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod("coerce", Object.class, Class.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), value, target);
    }

    @Test
    void nullValue_returnsNull() throws Exception {
        assertThat(coerce(null, String.class)).isNull();
    }

    @Test
    void alreadyAssignable_returnsValueUnchanged() throws Exception {
        LocalDate today = LocalDate.now();
        assertThat(coerce(today, LocalDate.class)).isSameAs(today);
    }

    @Test
    void blankNonStringTarget_returnsNull() throws Exception {
        // Boolean target with blank — falls through isInstance() → trim → isEmpty → null
        assertThat(coerce("   ", Boolean.class)).isNull();
    }

    @Test
    void stringTarget_alreadyAssignable_returnsAsIs() throws Exception {
        // String value to String target → caught by isInstance() short-circuit, returned untrimmed
        assertThat(coerce("  hello  ", String.class)).isEqualTo("  hello  ");
    }

    @Test
    void bigDecimalTarget_parses() throws Exception {
        assertThat(coerce("12.50", BigDecimal.class)).isEqualTo(new BigDecimal("12.50"));
    }

    @Test
    void bigDecimalTarget_unparseable_returnsNull() throws Exception {
        assertThat(coerce("not-a-number", BigDecimal.class)).isNull();
    }

    @Test
    void localDateTarget_isoString() throws Exception {
        assertThat(coerce("2025-01-15", LocalDate.class)).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void localDateTarget_isoDateTimeString_truncatesToDate() throws Exception {
        assertThat(coerce("2025-01-15T10:30:00", LocalDate.class)).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    void localDateTimeTarget_parses() throws Exception {
        assertThat(coerce("2025-01-15T10:30:00", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2025, 1, 15, 10, 30));
    }

    @Test
    void longWrapper_parses() throws Exception {
        assertThat(coerce("42", Long.class)).isEqualTo(42L);
    }

    @Test
    void longPrimitive_parses() throws Exception {
        assertThat(coerce("99", long.class)).isEqualTo(99L);
    }

    @Test
    void integerWrapper_parses() throws Exception {
        assertThat(coerce("7", Integer.class)).isEqualTo(7);
    }

    @Test
    void integerPrimitive_parses() throws Exception {
        assertThat(coerce("8", int.class)).isEqualTo(8);
    }

    @Test
    void booleanWrapper_trueAndFalse() throws Exception {
        assertThat(coerce("true", Boolean.class)).isEqualTo(true);
        assertThat(coerce("false", Boolean.class)).isEqualTo(false);
        assertThat(coerce("FALSE", Boolean.class)).isEqualTo(false);
        assertThat(coerce("0", Boolean.class)).isEqualTo(false);
        assertThat(coerce("1", Boolean.class)).isEqualTo(true);
        assertThat(coerce("yes", Boolean.class)).isEqualTo(true);
    }

    @Test
    void booleanPrimitive_zero_isFalse() throws Exception {
        assertThat(coerce("0", boolean.class)).isEqualTo(false);
        assertThat(coerce("true", boolean.class)).isEqualTo(true);
    }

    @Test
    void unsupportedTargetType_returnsNull() throws Exception {
        assertThat(coerce("X", java.util.Date.class)).isNull();
    }

    /** Drives safeRead(...)'s null/blank guards via reflection. */
    private static Object safeRead(Object source, String path) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod("safeRead", Object.class, String.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), source, path);
    }

    @Test
    void safeRead_nullSource_returnsNull() throws Exception {
        assertThat(safeRead(null, "anyPath")).isNull();
    }

    @Test
    void safeRead_nullPath_returnsNull() throws Exception {
        assertThat(safeRead(new Object(), null)).isNull();
    }

    @Test
    void safeRead_blankPath_returnsNull() throws Exception {
        assertThat(safeRead(new Object(), "   ")).isNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // capitalize — null and empty branches (private helper)
    // ─────────────────────────────────────────────────────────────────

    private static Object capitalize(String s) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), s);
    }

    @Test
    void capitalize_null_returnsNull() throws Exception {
        assertThat(capitalize(null)).isNull();
    }

    @Test
    void capitalize_empty_returnsEmpty() throws Exception {
        assertThat(capitalize("")).isEqualTo("");
    }

    // ─────────────────────────────────────────────────────────────────
    // hasEntries — non-null empty list branch
    // ─────────────────────────────────────────────────────────────────

    private static Object hasEntries(java.util.List<?> list) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod("hasEntries", java.util.List.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), list);
    }

    @Test
    void hasEntries_emptyList_returnsFalse() throws Exception {
        assertThat(hasEntries(java.util.List.of())).isEqualTo(false);
    }
}
