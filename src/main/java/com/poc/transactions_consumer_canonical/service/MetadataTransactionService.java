package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.metadata.ChildMetadata;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only orchestrator for the metadata-driven (v2) surface.
 *
 * <p>All write operations (upsert, delete) have been moved to the Kafka
 * canonical pipeline. Responsibilities here are:
 * <ul>
 *   <li>Fetch a parent row with its full child graph ({@link #findByPk}).</li>
 *   <li>Expose the column catalog for all registered tables ({@link #listMetadata})
 *       or a single table ({@link #getMetadata}) — the discovery endpoints.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataTransactionService {

    private final MetadataRegistry registry;
    private final GenericTableRepository repo;

    // ── Data query ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findByPk(String parentAlias, Object pk) {
        TableMetadata parent = registry.require(parentAlias);
        return repo.findByPk(parent.getName(), pk).map(parentRow -> {
            for (ChildMetadata cm : parent.getChildren()) {
                TableMetadata child = registry.require(cm.getTableRef());
                if (cm.isOneToOne()) {
                    parentRow.put(cm.getJsonName(),
                            repo.findByFk(child.getName(), cm.getChildKey(), pk).orElse(null));
                } else {
                    parentRow.put(cm.getJsonName(),
                            repo.findAllByFk(child.getName(), cm.getChildKey(), pk));
                }
            }
            return parentRow;
        });
    }

    // ── Metadata discovery ────────────────────────────────────────────────────

    /**
     * Returns the column catalog for every registered table, sorted by alias.
     * Each entry has the same shape as {@link #getMetadata(String)}.
     */
    public List<Map<String, Object>> listMetadata() {
        return registry.all().stream()
                .sorted((a, b) -> a.getAlias().compareToIgnoreCase(b.getAlias()))
                .map(this::toMetadataView)
                .toList();
    }

    /**
     * Returns the column catalog for a single table identified by alias or DB name.
     * Throws {@link IllegalArgumentException} (→ 500 via global handler) if the alias
     * is unknown — callers should treat an unknown alias as a client error; the
     * controller may choose to wrap this in a 404 if desired.
     */
    public Map<String, Object> getMetadata(String alias) {
        TableMetadata t = registry.require(alias);
        return toMetadataView(t);
    }

    /**
     * Converts a {@link TableMetadata} into a JSON-friendly view map:
     * <pre>
     * {
     *   "name":           "SEND_TRANSACTIONS",
     *   "alias":          "send-transactions",
     *   "pk":             "TRAN_ID",
     *   "pkJsonName":     "tranId",
     *   "defaultOrderBy": "TRAN_CRTE_DT DESC",
     *   "columns": [
     *     {
     *       "jsonName":   "tranId",
     *       "dbColumn":   "TRAN_ID",
     *       "sqlType":    "VARCHAR",
     *       "pk":         true,
     *       "required":   true,
     *       "readOnly":   false,
     *       "audit":      false,
     *       "insertOnly": false,
     *       "clob":       false,
     *       "nullGuard":  false,
     *       "maxLength":  50,
     *       "pattern":    null,
     *       "converter":  "PASSTHROUGH"
     *     }, ...
     *   ],
     *   "children": [
     *     { "jsonName": "tranDtl", "tableRef": "SEND_TRAN_DTL",
     *       "cardinality": "ONE_TO_ONE", "childKey": "TRAN_ID" }, ...
     *   ]
     * }
     * </pre>
     */
    private Map<String, Object> toMetadataView(TableMetadata t) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name",           t.getName());
        view.put("alias",          t.getAlias());
        view.put("pk",             t.getPk());
        view.put("pkJsonName",     t.getPkJsonName());
        view.put("defaultOrderBy", t.getDefaultOrderBy());

        List<Map<String, Object>> cols = new ArrayList<>(t.getColumns().size());
        for (ColumnMetadata c : t.getColumns()) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("jsonName",   c.getJsonName());
            col.put("dbColumn",   c.getDbColumn());
            col.put("sqlType",    c.getSqlType());
            col.put("pk",         c.isPk());
            col.put("required",   c.isRequired());
            col.put("readOnly",   c.isReadOnly());
            col.put("audit",      c.isAudit());
            col.put("insertOnly", c.isInsertOnly());
            col.put("clob",       c.isClob());
            col.put("nullGuard",  c.isNullGuard());
            col.put("maxLength",  c.getMaxLength());
            col.put("pattern",    c.getPattern());
            col.put("converter",  c.converterName());
            cols.add(col);
        }
        view.put("columns", cols);

        List<Map<String, Object>> children = new ArrayList<>(t.getChildren().size());
        for (ChildMetadata cm : t.getChildren()) {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("jsonName",    cm.getJsonName());
            child.put("tableRef",    cm.getTableRef());
            child.put("cardinality", cm.getCardinality());
            child.put("childKey",    cm.getChildKey());
            if (cm.getIdJsonName() != null) child.put("idJsonName", cm.getIdJsonName());
            children.add(child);
        }
        view.put("children", children);

        return view;
    }
}
