package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a row into a {@link LinkedHashMap} keyed by each column's {@code jsonName},
 * applying the column's {@link ValueConverter} for the JDBC → JSON-friendly type step.
 * <p>
 * TIMESTAMP read uses {@code Timestamp.toInstant().atOffset(UTC)} — never the
 * JVM-default-timezone-dependent {@code toLocalDateTime()}.
 */
public class GenericRowMapper implements RowMapper<Map<String, Object>> {

    private final TableMetadata table;
    private final Converters converters;

    public GenericRowMapper(TableMetadata table, Converters converters) {
        this.table = table;
        this.converters = converters;
    }

    @Override
    @Nullable
    @SuppressWarnings("java:S2638") // RowMapper.mapRow is declared @Nullable in the interface — override is contract-compatible
    public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(table.getColumns().size());
        for (ColumnMetadata col : table.getColumns()) {
            Object raw = readRaw(rs, col);
            Object converted = converters.forName(col.converterName()).fromJdbc(raw, col);
            // Skip jsonName == null (pure DB-only columns); otherwise emit even nulls
            if (col.getJsonName() != null && !col.getJsonName().isBlank()) {
                row.put(col.getJsonName(), converted);
            }
        }
        return row;
    }

    @Nullable
    private Object readRaw(ResultSet rs, ColumnMetadata col) throws SQLException {
        String type = col.getSqlType() == null ? "" : col.getSqlType().toUpperCase(Locale.ROOT);
        String dbCol = col.getDbColumn();
        if (dbCol == null || dbCol.isBlank()) {
            return null;
        }
        return switch (type) {
            case "VARCHAR", "CLOB" -> rs.getString(dbCol);
            case "NUMERIC"          -> rs.getBigDecimal(dbCol);
            case "INTEGER"          -> rs.getObject(dbCol, Integer.class);
            case "TIMESTAMP" -> {
                Timestamp ts = rs.getTimestamp(dbCol);
                yield ts == null
                        ? null
                        : ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
            }
            case "DATE" -> {
                Date d = rs.getDate(dbCol);
                yield d == null ? null : d.toLocalDate();
            }
            default -> rs.getObject(dbCol);
        };
    }
}
