package com.poc.transactions_consumer_canonical.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Types;

/**
 * Metadata for a single database column.
 * <p>
 * Deserialized from YAML. Drives SQL generation, JDBC parameter binding,
 * ResultSet reading, and validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColumnMetadata {

    /** JSON field name (camelCase, e.g. "tranAmt"). */
    private String jsonName;

    /** Oracle column name (UPPER_SNAKE, e.g. "TRAN_AMT"). */
    private String dbColumn;

    /** One of: VARCHAR | NUMERIC | TIMESTAMP | DATE | CLOB | INTEGER. */
    private String sqlType;

    /** Max length for VARCHAR validation; null = unbounded. */
    private Integer maxLength;

    /** When true, MetadataValidator rejects null. */
    private Boolean required;

    /** Primary-key column flag. PKs are never null-guarded and bind directly. */
    private Boolean pk;

    /**
     * When true, MERGE UPDATE SET uses {@code CASE WHEN :p IS NOT NULL THEN TO_CLOB(:p) ELSE t.COL END}
     * (Oracle restriction — COALESCE cannot mix VARCHAR2 bind param with CLOB column).
     * Parameter is still bound as {@link Types#VARCHAR} because TO_CLOB accepts VARCHAR2.
     */
    private Boolean clob;

    /**
     * DB-managed audit column (CRTE_TS, UPDT_TS, RPLCTN_UPDT_TS).
     * Omitted from MERGE parameter binding entirely; rendered as SYSTIMESTAMP literals
     * in the generated SQL.
     */
    private Boolean audit;

    /**
     * When true (default), MERGE UPDATE SET uses {@code COALESCE(:p, t.COL)} so
     * incoming null preserves existing DB value.
     * Set to false for PKs and CLOB-CASE-WHEN columns.
     */
    private Boolean nullGuard;

    /** Read-only column (CRTE_TS/UPDT_TS); appears only in SELECT output. */
    private Boolean readOnly;

    /** Insert-only column (e.g. CRTE_USER_NAM); appears in INSERT VALUES but not in UPDATE SET. */
    private Boolean insertOnly;

    /** ValueConverter name. Default PASSTHROUGH. Built-in: PASSTHROUGH, BOOLEAN_AS_INT. */
    private String converter;

    /** Optional regex applied to String payload values during validation. */
    private String pattern;

    // ──────────────────────────────────────────────────────────
    // Convenience accessors with sensible defaults
    // ──────────────────────────────────────────────────────────

    public boolean isPk()         { return Boolean.TRUE.equals(pk); }
    public boolean isClob()       { return Boolean.TRUE.equals(clob); }
    public boolean isAudit()      { return Boolean.TRUE.equals(audit); }
    public boolean isReadOnly()   { return Boolean.TRUE.equals(readOnly); }
    public boolean isInsertOnly() { return Boolean.TRUE.equals(insertOnly); }
    public boolean isRequired()   { return Boolean.TRUE.equals(required); }
    /** Defaults to true unless explicitly set to false. */
    public boolean isNullGuard()  { return nullGuard == null || nullGuard; }

    public String converterName() {
        return converter == null || converter.isBlank() ? "PASSTHROUGH" : converter;
    }

    /** Translates the {@code sqlType} string into a {@link java.sql.Types} constant. */
    public int jdbcType() {
        return switch (sqlType == null ? "" : sqlType.toUpperCase()) {
            case "VARCHAR", "CLOB" -> Types.VARCHAR; // CLOB bound as VARCHAR — TO_CLOB() promotes it
            case "NUMERIC"          -> Types.NUMERIC;
            case "INTEGER"          -> Types.INTEGER;
            case "TIMESTAMP"        -> Types.TIMESTAMP;
            case "DATE"             -> Types.DATE;
            default -> throw new IllegalStateException(
                    "Unknown sqlType '" + sqlType + "' for column " + dbColumn);
        };
    }
}
