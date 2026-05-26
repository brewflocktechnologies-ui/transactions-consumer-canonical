package com.poc.transactions_consumer_canonical.canonicalmapping;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-driven tests for the private helpers of the Map-based
 * {@link CanonicalMappingEngine}. Type coercion was removed when the engine
 * stopped writing into typed POJOs — coercion now happens at JDBC bind time
 * inside {@code ValueConverter}.
 *
 * <p>This file is retained (and renamed for what it actually covers) so the
 * residual branch coverage on the private helpers is preserved.
 */
class CanonicalMappingEngineCoerceTest {

    /** Drives the private {@code safeRead(Map, String)} via reflection. */
    private static Object safeRead(Map<String, Object> source, String path) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod(
                "safeRead", Map.class, String.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), source, path);
    }

    @Test
    void safeRead_nullSource_returnsNull() throws Exception {
        assertThat(safeRead(null, "anyPath")).isNull();
    }

    @Test
    void safeRead_nullPath_returnsNull() throws Exception {
        assertThat(safeRead(new HashMap<>(), null)).isNull();
    }

    @Test
    void safeRead_blankPath_returnsNull() throws Exception {
        assertThat(safeRead(new HashMap<>(), "   ")).isNull();
    }

    @Test
    void safeRead_traversesNestedMap() throws Exception {
        Map<String, Object> deep = new HashMap<>();
        deep.put("leaf", "value");
        Map<String, Object> source = new HashMap<>();
        source.put("outer", deep);
        assertThat(safeRead(source, "outer.leaf")).isEqualTo("value");
    }

    @Test
    void safeRead_returnsNullOnMissingSegment() throws Exception {
        Map<String, Object> source = new HashMap<>();
        source.put("outer", new HashMap<>());
        assertThat(safeRead(source, "outer.missing")).isNull();
    }

    @Test
    void safeRead_returnsNullWhenSegmentIsNotAMap() throws Exception {
        Map<String, Object> source = new HashMap<>();
        source.put("outer", "stringValue"); // not a Map — traversal stops
        assertThat(safeRead(source, "outer.inner")).isNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // hasEntries
    // ─────────────────────────────────────────────────────────────────

    private static Object hasEntries(java.util.List<?> list) throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod("hasEntries", java.util.List.class);
        m.setAccessible(true);
        return m.invoke(new CanonicalMappingEngine(), list);
    }

    @Test
    void hasEntries_emptyList_returnsFalse() throws Exception {
        assertThat(hasEntries(List.of())).isEqualTo(false);
    }

    @Test
    void hasEntries_nullList_returnsFalse() throws Exception {
        assertThat(hasEntries(null)).isEqualTo(false);
    }

    @Test
    void hasEntries_populatedList_returnsTrue() throws Exception {
        assertThat(hasEntries(List.of("x"))).isEqualTo(true);
    }

    // ─────────────────────────────────────────────────────────────────
    // applyMappings catch branch — exceptions while reading are swallowed
    // ─────────────────────────────────────────────────────────────────

    @Test
    void applyMappings_swallowsThrowingMapGet() throws Exception {
        Method m = CanonicalMappingEngine.class.getDeclaredMethod(
                "applyMappings", List.class, Map.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> target = new LinkedHashMap<>();
        Object count = m.invoke(new CanonicalMappingEngine(),
                List.of(new FieldMapping("anyKey", "anyTarget", null)),
                new ThrowingGetMap(),
                target);
        assertThat(count).isEqualTo(0);
        assertThat(target).isEmpty();
    }

    /**
     * Named subclass instead of an anonymous-class extension of {@link HashMap}
     * so static analysis doesn't flag a non-serializable anonymous inner class.
     */
    private static final class ThrowingGetMap extends HashMap<String, Object> {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Object get(Object key) {
            throw new IllegalStateException("boom");
        }
    }
}
