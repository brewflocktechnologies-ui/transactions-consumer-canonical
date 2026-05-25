package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Generates Oracle SQL from {@link TableMetadata}. Results are cached per
 * (table, operation) — built lazily on first request.
 * <p>
 * <strong>MERGE UPDATE SET emission rules</strong> (per column):
 * <ul>
 *   <li>{@code audit:true} and dbColumn in {UPDT_TS, RPLCTN_UPDT_TS} → {@code COL = SYSTIMESTAMP}</li>
 *   <li>{@code audit:true} (CRTE_TS only) → omitted from UPDATE SET</li>
 *   <li>{@code clob:true} → {@code COL = CASE WHEN :p IS NOT NULL THEN TO_CLOB(:p) ELSE t.COL END}</li>
 *   <li>{@code insertOnly:true} → omitted from UPDATE SET</li>
 *   <li>{@code pk:true} → omitted (used in ON clause)</li>
 *   <li>{@code nullGuard:true} (default) → {@code COL = COALESCE(:p, t.COL)}</li>
 *   <li>{@code nullGuard:false} → {@code COL = :p}</li>
 * </ul>
 */
@Component
public class SqlBuilder {

    private static final String UPDATED_AUDIT_COL_UPDT  = "UPDT_TS";
    private static final String UPDATED_AUDIT_COL_RPLCT = "RPLCTN_UPDT_TS";
    private static final String CREATED_AUDIT_COL       = "CRTE_TS";

    private static final String SQL_SELECT      = "SELECT ";
    private static final String SQL_FROM        = " FROM ";
    private static final String SQL_WHERE       = " WHERE ";

    /** key = TABLE_NAME + "|" + op */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String buildMerge(TableMetadata t)            { return cache.computeIfAbsent(key(t, "MERGE"),  k -> doBuildMerge(t)); }
    public String buildSelectByPk(TableMetadata t)       { return cache.computeIfAbsent(key(t, "SEL_PK"), k -> doBuildSelectByPk(t)); }
    public String buildSelectByFk(TableMetadata t, String fkCol) { return doBuildSelectByFk(t, fkCol); }

    private String doBuildMerge(TableMetadata t) {
        String pkJson = t.pkColumn().getJsonName();
        String pkDb   = t.getPk();

        StringBuilder sb = new StringBuilder(2048);
        sb.append("MERGE INTO ").append(t.qualifiedName()).append(" t\n");
        sb.append("USING (SELECT :").append(pkJson).append(" AS ").append(pkDb).append(" FROM DUAL) src\n");
        sb.append("ON (t.").append(pkDb).append(" = src.").append(pkDb).append(")\n");
        sb.append("WHEN MATCHED THEN UPDATE SET\n    ")
          .append(String.join(",\n    ", buildSetClauses(t))).append("\n");
        sb.append("WHEN NOT MATCHED THEN INSERT (\n    ")
          .append(String.join(", ", buildInsertCols(t)))
          .append("\n) VALUES (\n    ")
          .append(String.join(", ", buildInsertValues(t)))
          .append("\n)");
        return sb.toString();
    }

    private List<String> buildSetClauses(TableMetadata t) {
        List<String> setClauses = new ArrayList<>();
        for (ColumnMetadata c : t.getColumns()) {
            if (c.isPk() || (c.isReadOnly() && !c.isAudit()) || c.isInsertOnly()) continue;
            setClause(c).ifPresent(setClauses::add);
        }
        return setClauses;
    }

    private Optional<String> setClause(ColumnMetadata c) {
        String col = c.getDbColumn().toUpperCase(Locale.ROOT);
        if (c.isAudit()) {
            if (CREATED_AUDIT_COL.equals(col)) return Optional.empty();
            if (UPDATED_AUDIT_COL_UPDT.equals(col) || UPDATED_AUDIT_COL_RPLCT.equals(col)) {
                return Optional.of(c.getDbColumn() + " = SYSTIMESTAMP");
            }
            return Optional.empty();
        }
        if (c.isClob()) {
            return Optional.of(c.getDbColumn()
                    + " = CASE WHEN :" + c.getJsonName() + " IS NOT NULL THEN TO_CLOB(:"
                    + c.getJsonName() + ") ELSE t." + c.getDbColumn() + " END");
        }
        if (c.isNullGuard()) {
            return Optional.of(c.getDbColumn() + " = COALESCE(:" + c.getJsonName()
                    + ", t." + c.getDbColumn() + ")");
        }
        return Optional.of(c.getDbColumn() + " = :" + c.getJsonName());
    }

    private List<String> buildInsertCols(TableMetadata t) {
        List<String> cols = new ArrayList<>();
        for (ColumnMetadata c : t.getColumns()) {
            if (c.isReadOnly() && !c.isAudit()) continue;
            cols.add(c.getDbColumn());
        }
        return cols;
    }

    private List<String> buildInsertValues(TableMetadata t) {
        List<String> values = new ArrayList<>();
        for (ColumnMetadata c : t.getColumns()) {
            if (c.isReadOnly() && !c.isAudit()) continue;
            values.add(c.isAudit() ? "SYSTIMESTAMP" : ":" + c.getJsonName());
        }
        return values;
    }

    private String doBuildSelectByPk(TableMetadata t) {
        return SQL_SELECT + columnList(t) + SQL_FROM + t.qualifiedName()
                + SQL_WHERE + t.getPk() + " = :" + t.pkColumn().getJsonName();
    }

    private String doBuildSelectByFk(TableMetadata t, String fkCol) {
        return SQL_SELECT + columnList(t) + SQL_FROM + t.qualifiedName()
                + SQL_WHERE + fkCol + " = :fkValue";
    }

    private String columnList(TableMetadata t) {
        return t.getColumns().stream()
                .map(ColumnMetadata::getDbColumn)
                .collect(Collectors.joining(", "));
    }

    private String key(TableMetadata t, String op) {
        return t.getName().toUpperCase(Locale.ROOT) + "|" + op;
    }
}
