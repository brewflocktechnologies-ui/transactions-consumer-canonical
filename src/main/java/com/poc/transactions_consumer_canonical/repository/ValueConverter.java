package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Converts payload values (typically deserialised from JSON as String/Number/Boolean/null)
 * to JDBC-friendly Java types, and back again for ResultSet reads.
 * <p>
 * Resolve an instance via {@link Converters#forName(String, ObjectMapper)} — the
 * shared Jackson {@link ObjectMapper} is needed so we get JSR-310 (LocalDateTime / LocalDate)
 * parsing for free.
 */
public interface ValueConverter {

    /** Coerce a payload value to a type the Oracle JDBC driver accepts for {@code col}. */
    Object toJdbc(Object payloadValue, ColumnMetadata col);

    /** Coerce a value read from {@link java.sql.ResultSet} into a JSON-friendly type. */
    Object fromJdbc(Object dbValue, ColumnMetadata col);

    /**
     * Default passthrough — handles ISO-8601 strings → LocalDateTime/LocalDate,
     * Number → BigDecimal for NUMERIC, and leaves String/Boolean alone.
     */
    final class Passthrough implements ValueConverter {
        private final ObjectMapper json;

        public Passthrough(ObjectMapper json) {
            this.json = json;
        }

        @Override
        public Object toJdbc(Object v, ColumnMetadata col) {
            if (v == null) return null;
            String t = col.getSqlType() == null ? "" : col.getSqlType().toUpperCase(Locale.ROOT);
            return switch (t) {
                case "TIMESTAMP" -> v instanceof LocalDateTime ldt ? ldt
                        : json.convertValue(v, LocalDateTime.class);
                case "DATE" -> v instanceof LocalDate ld ? ld
                        : json.convertValue(v, LocalDate.class);
                case "NUMERIC"  -> coerceToDecimal(v);
                case "INTEGER"  -> coerceToInteger(v);
                // VARCHAR, CLOB
                default -> v instanceof String s ? s : v.toString();
            };
        }

        private static Object coerceToDecimal(Object v) {
            if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return new BigDecimal(n.toString());
            return new BigDecimal(v.toString());
        }

        private static Object coerceToInteger(Object v) {
            if (v instanceof Integer i) return i;
            if (v instanceof Number n) return n.intValue();
            return Integer.parseInt(v.toString());
        }

        @Override
        public Object fromJdbc(Object dbValue, ColumnMetadata col) {
            return dbValue; // GenericRowMapper already used type-specific getters
        }
    }

    /** Boolean ↔ NUMBER(1,0): true → 1, false → 0, null → null. */
    final class BooleanAsInt implements ValueConverter {
        @Override
        public Object toJdbc(Object v, ColumnMetadata col) {
            if (v == null) return null;
            if (v instanceof Boolean b) return b.booleanValue() ? 1 : 0;
            if (v instanceof Number n) return n.intValue() == 0 ? 0 : 1;
            if (v instanceof String s) {
                if (s.equalsIgnoreCase("true") || s.equals("1"))  return 1;
                if (s.equalsIgnoreCase("false") || s.equals("0")) return 0;
            }
            throw new IllegalArgumentException("BOOLEAN_AS_INT: cannot convert " + v
                    + " for column " + col.getDbColumn());
        }

        @Override
        public Object fromJdbc(Object dbValue, ColumnMetadata col) {
            if (dbValue == null) return null;
            if (dbValue instanceof Number n) return n.intValue() == 1;
            if (dbValue instanceof Boolean b) return b;
            return null;
        }
    }
}
