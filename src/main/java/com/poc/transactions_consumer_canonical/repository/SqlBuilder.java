package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** key = TABLE_NAME + "|" + op */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────

    public String buildMerge(TableMetadata t)            { return cache.computeIfAbsent(key(t, "MERGE"),  k -> doBuildMerge(t)); }
    public String buildSelectByPk(TableMetadata t)       { return cache.computeIfAbsent(key(t, "SEL_PK"), k -> doBuildSelectByPk(t)); }
    public String buildSelectByFk(TableMetadata t, String fkCol) { return doBuildSelectByFk(t, fkCol); }
    public String buildSelectPage(TableMetadata t)       { return cache.computeIfAbsent(key(t, "SEL_PG"), k -> doBuildSelectPage(t)); }
    public String buildCount(TableMetadata t)            { return cache.computeIfAbsent(key(t, "COUNT"),  k -> "SELECT COUNT(*) FROM " + t.qualifiedName()); }
    public String buildDeleteByPk(TableMetadata t)       { return cache.computeIfAbsent(key(t, "DEL_PK"), k -> "DELETE FROM " + t.qualifiedName() + " WHERE " + t.getPk() + " = :" + t.pkColumn().getJsonName()); }
    public String buildDeleteByFk(TableMetadata t, String fkCol) { return "DELETE FROM " + t.qualifiedName() + " WHERE " + fkCol + " = :fkValue"; }
    public String buildDeleteByFkNotIn(TableMetadata t, String fkCol) { return "DELETE FROM " + t.qualifiedName() + " WHERE " + fkCol + " = :fkValue AND " + t.getPk() + " NOT IN (:keepIds)"; }

    // ──────────────────────────────────────────────────────────
    // Implementations
    // ──────────────────────────────────────────────────────────

    private String doBuildMerge(TableMetadata t) {
        String pkJson = t.pkColumn().getJsonName();
        String pkDb   = t.getPk();

        StringBuilder sb = new StringBuilder(2048);
        sb.append("MERGE INTO ").append(t.qualifiedName()).append(" t\n");
        sb.append("USING (SELECT :").append(pkJson).append(" AS ").append(pkDb).append(" FROM DUAL) src\n");
        sb.append("ON (t.").append(pkDb).append(" = src.").append(pkDb).append(")\n");

        // ── WHEN MATCHED THEN UPDATE SET ──
        List<String> setClauses = new ArrayList<>();
        for (ColumnMetadata c : t.getColumns()) {
            if (c.isPk())         continue;
            if (c.isReadOnly() && !c.isAudit()) continue;
            if (c.isInsertOnly()) continue;

            String col = c.getDbColumn().toUpperCase(Locale.ROOT);
            if (c.isAudit()) {
                if (CREATED_AUDIT_COL.equals(col)) continue;
                if (UPDATED_AUDIT_COL_UPDT.equals(col) || UPDATED_AUDIT_COL_RPLCT.equals(col)) {
                    setClauses.add(c.getDbColumn() + " = SYSTIMESTAMP");
                }
                continue;
            }
            if (c.isClob()) {
                setClauses.add(c.getDbColumn()
                        + " = CASE WHEN :" + c.getJsonName() + " IS NOT NULL THEN TO_CLOB(:"
                        + c.getJsonName() + ") ELSE t." + c.getDbColumn() + " END");
            } else if (c.isNullGuard()) {
                setClauses.add(c.getDbColumn() + " = COALESCE(:" + c.getJsonName()
                        + ", t." + c.getDbColumn() + ")");
            } else {
                setClauses.add(c.getDbColumn() + " = :" + c.getJsonName());
            }
        }
        sb.append("WHEN MATCHED THEN UPDATE SET\n    ")
          .append(String.join(",\n    ", setClauses)).append("\n");

        // ── WHEN NOT MATCHED THEN INSERT ──
        List<String> insertCols   = new ArrayList<>();
        List<String> insertValues = new ArrayList<>();
        for (ColumnMetadata c : t.getColumns()) {
            if (c.isReadOnly() && !c.isAudit()) continue;
            String col = c.getDbColumn().toUpperCase(Locale.ROOT);
            insertCols.add(c.getDbColumn());
            if (c.isAudit()) {
                // All known audit cols get SYSTIMESTAMP on insert
                if (CREATED_AUDIT_COL.equals(col)
                        || UPDATED_AUDIT_COL_UPDT.equals(col)
                        || UPDATED_AUDIT_COL_RPLCT.equals(col)) {
                    insertValues.add("SYSTIMESTAMP");
                } else {
                    insertValues.add("SYSTIMESTAMP"); // any other audit col follows same convention
                }
            } else {
                insertValues.add(":" + c.getJsonName());
            }
        }
        sb.append("WHEN NOT MATCHED THEN INSERT (\n    ")
          .append(String.join(", ", insertCols))
          .append("\n) VALUES (\n    ")
          .append(String.join(", ", insertValues))
          .append("\n)");

        return sb.toString();
    }

    private String doBuildSelectByPk(TableMetadata t) {
        return "SELECT " + columnList(t) + " FROM " + t.qualifiedName()
                + " WHERE " + t.getPk() + " = :" + t.pkColumn().getJsonName();
    }

    private String doBuildSelectByFk(TableMetadata t, String fkCol) {
        return "SELECT " + columnList(t) + " FROM " + t.qualifiedName()
                + " WHERE " + fkCol + " = :fkValue";
    }

    private String doBuildSelectPage(TableMetadata t) {
        String orderBy = (t.getDefaultOrderBy() == null || t.getDefaultOrderBy().isBlank())
                ? t.getPk()
                : t.getDefaultOrderBy();
        return "SELECT " + columnList(t)
                + " FROM " + t.qualifiedName()
                + " ORDER BY " + orderBy
                + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
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
