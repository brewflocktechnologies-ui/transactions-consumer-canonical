package com.poc.transactions_consumer_canonical.canonicalmapping;

import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility that recursively wraps a {@code Map<String,Object>} payload (as produced by
 * Jackson when deserialising arbitrary JSON) into {@link LinkedCaseInsensitiveMap}
 * instances so downstream code can look up keys case-insensitively without thinking
 * about it.
 *
 * <p>JSON arrays are walked too: any {@code Map}-shaped element is wrapped, primitives
 * are passed through. Insertion order is preserved.
 */
public final class CaseInsensitiveJsonMap {

    private CaseInsensitiveJsonMap() { /* utility */ }

    /**
     * Recursively wraps the supplied object. Returns:
     * <ul>
     *   <li>A new {@link LinkedCaseInsensitiveMap} when the input is a {@code Map} —
     *       every value is wrapped recursively.</li>
     *   <li>A new {@code List} with each element wrapped recursively when the input
     *       is a {@code List}.</li>
     *   <li>The input unchanged for primitives, {@code null}, or any other type.</li>
     * </ul>
     */
    public static Object wrap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> wrapped = new LinkedCaseInsensitiveMap<>(map.size(), Locale.ROOT);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                wrapped.put(e.getKey().toString(), wrap(e.getValue()));
            }
            return wrapped;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(wrap(item));
            return out;
        }
        return value;
    }

    /** Convenience overload — returns an empty case-insensitive map when {@code source} is {@code null}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> wrapMap(Map<String, Object> source) {
        if (source == null) return new LinkedCaseInsensitiveMap<>(0, Locale.ROOT);
        return (Map<String, Object>) wrap(source);
    }
}
