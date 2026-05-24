package com.poc.transactions_consumer_canonical.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Metadata for a single Oracle table. Deserialized from a YAML file under
 * {@code classpath:metadata/*.yaml}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableMetadata {

    /** Oracle table name (e.g. "SEND_TRANSACTIONS"). */
    private String name;

    /** Oracle schema (e.g. "SEND_TXN_OWNER"). */
    private String schema;

    /** URL-friendly alias used as path segment (e.g. "send-transactions"). */
    private String alias;

    /** Primary-key column name (DB column, e.g. "TRAN_ID"). */
    private String pk;

    /** Primary-key JSON field name (e.g. "tranId"). */
    private String pkJsonName;

    /** ORDER BY clause for paged SELECT (e.g. "TRAN_CRTE_DT DESC"). */
    private String defaultOrderBy;

    @Builder.Default
    private List<ColumnMetadata> columns = new ArrayList<>();

    @Builder.Default
    private List<ChildMetadata> children = new ArrayList<>();

    // ──────────────────────────────────────────────────────────
    // Lookups & validation
    // ──────────────────────────────────────────────────────────

    /** Look up a column by jsonName. */
    public Optional<ColumnMetadata> columnByJsonName(String jsonName) {
        return columns.stream().filter(c -> jsonName.equals(c.getJsonName())).findFirst();
    }

    /** Look up a column by dbColumn (case-insensitive). */
    public Optional<ColumnMetadata> columnByDbColumn(String dbColumn) {
        return columns.stream()
                .filter(c -> dbColumn.equalsIgnoreCase(c.getDbColumn()))
                .findFirst();
    }

    /** Resolve a DB column name to its JSON name (for FK injection in service layer). */
    public String jsonNameForColumn(String dbColumn) {
        return columnByDbColumn(dbColumn)
                .map(ColumnMetadata::getJsonName)
                .orElseThrow(() -> new IllegalStateException(
                        "No column with dbColumn=" + dbColumn + " on table " + name));
    }

    public ColumnMetadata pkColumn() {
        return columnByDbColumn(pk).orElseThrow(() -> new IllegalStateException(
                "PK column '" + pk + "' not declared in columns for table " + name));
    }

    /** Returns "SCHEMA.NAME" if schema is set, otherwise just NAME. */
    public String qualifiedName() {
        return (schema == null || schema.isBlank()) ? name : schema + "." + name;
    }

    /** Validates internal consistency. Throws on any problem. Called by MetadataRegistry at startup. */
    public void validate() {
        validateRequiredFields();
        validateColumns();
        validateChildren();
    }

    // ── Validation helpers ────────────────────────────────────

    private void validateRequiredFields() {
        if (name == null || name.isBlank())
            throw new IllegalStateException("Table metadata missing 'name'");
        if (alias == null || alias.isBlank())
            throw new IllegalStateException("Table " + name + " missing 'alias'");
        if (pk == null || pk.isBlank())
            throw new IllegalStateException("Table " + name + " missing 'pk'");
        if (pkJsonName == null || pkJsonName.isBlank())
            throw new IllegalStateException("Table " + name + " missing 'pkJsonName'");
        if (columns == null || columns.isEmpty())
            throw new IllegalStateException("Table " + name + " has no columns");
    }

    private void validateColumns() {
        Set<String> seenDb   = new HashSet<>();
        Set<String> seenJson = new HashSet<>();
        boolean pkFound = false;
        for (ColumnMetadata c : columns) {
            validateColumn(c, seenDb, seenJson);
            if (c.isPk()) pkFound = true;
            c.jdbcType(); // sanity: jdbcType() must not throw
        }
        if (!pkFound)
            throw new IllegalStateException("Table " + name + " has no column with pk:true");
        if (pkColumn() == null)
            throw new IllegalStateException("Table " + name + " pk '" + pk
                    + "' does not match any column with pk:true");
    }

    private void validateColumn(ColumnMetadata c, Set<String> seenDb, Set<String> seenJson) {
        if (c.getDbColumn() == null || c.getDbColumn().isBlank())
            throw new IllegalStateException("Table " + name + " has a column missing dbColumn");
        if (c.getSqlType() == null || c.getSqlType().isBlank())
            throw new IllegalStateException("Table " + name + " column " + c.getDbColumn()
                    + " missing sqlType");
        if (!seenDb.add(c.getDbColumn().toUpperCase()))
            throw new IllegalStateException("Table " + name + " duplicate dbColumn "
                    + c.getDbColumn());
        if (c.getJsonName() != null && !c.getJsonName().isBlank()
                && !seenJson.add(c.getJsonName()))
            throw new IllegalStateException("Table " + name + " duplicate jsonName "
                    + c.getJsonName());
        if (c.isClob() && c.isNullGuard())
            throw new IllegalStateException("Table " + name + " column " + c.getDbColumn()
                    + ": clob:true requires nullGuard:false (Oracle COALESCE/CLOB type mismatch)");
    }

    private void validateChildren() {
        if (children == null) return;
        for (ChildMetadata cm : children) {
            validateChild(cm);
        }
    }

    private void validateChild(ChildMetadata cm) {
        if (cm.getTableRef() == null || cm.getTableRef().isBlank())
            throw new IllegalStateException("Table " + name + " child " + cm.getJsonName()
                    + " missing tableRef");
        if (!cm.isOneToOne() && !cm.isOneToMany())
            throw new IllegalStateException("Table " + name + " child " + cm.getJsonName()
                    + " has invalid cardinality " + cm.getCardinality());
        if (cm.getChildKey() == null || cm.getChildKey().isBlank())
            throw new IllegalStateException("Table " + name + " child " + cm.getJsonName()
                    + " missing childKey");
    }
}
