package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.metadata.ChildMetadata;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SqlBuilder} — asserts that the generated SQL contains
 * the expected fragments for each special case. Substring assertions (not
 * golden strings) make the tests resilient to whitespace and ordering changes.
 */
class SqlBuilderTest {

    private SqlBuilder sql;
    private TableMetadata t;

    @BeforeEach
    void setUp() {
        sql = new SqlBuilder();
        t = TableMetadata.builder()
                .name("T1")
                .schema("APP")
                .alias("t1")
                .pk("ID")
                .pkJsonName("id")
                .defaultOrderBy("CRTE_TS DESC")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("name").dbColumn("NAME").sqlType("VARCHAR").build(),
                        ColumnMetadata.builder().jsonName("doc").dbColumn("DOC").sqlType("CLOB")
                                .clob(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("active").dbColumn("ACTIVE").sqlType("NUMERIC")
                                .converter("BOOLEAN_AS_INT").build(),
                        ColumnMetadata.builder().jsonName("crteUserNam").dbColumn("CRTE_USER_NAM")
                                .sqlType("VARCHAR").insertOnly(true).build(),
                        ColumnMetadata.builder().jsonName("crteTs").dbColumn("CRTE_TS").sqlType("TIMESTAMP")
                                .audit(true).readOnly(true).build(),
                        ColumnMetadata.builder().jsonName("updtTs").dbColumn("UPDT_TS").sqlType("TIMESTAMP")
                                .audit(true).readOnly(true).build()
                ))
                .children(List.of())
                .build();
        t.validate();
    }

    @Test
    void merge_uses_qualified_table_and_pk_on_clause() {
        String merge = sql.buildMerge(t);
        assertTrue(merge.contains("MERGE INTO APP.T1 t"));
        assertTrue(merge.contains("USING (SELECT :id AS ID FROM DUAL) src"));
        assertTrue(merge.contains("ON (t.ID = src.ID)"));
    }

    @Test
    void merge_emits_coalesce_null_guard_for_default_columns() {
        String merge = sql.buildMerge(t);
        assertTrue(merge.contains("NAME = COALESCE(:name, t.NAME)"),
                () -> "expected COALESCE null-guard:\n" + merge);
    }

    @Test
    void merge_emits_to_clob_case_when_for_clob_columns() {
        String merge = sql.buildMerge(t);
        assertTrue(merge.contains("DOC = CASE WHEN :doc IS NOT NULL THEN TO_CLOB(:doc) ELSE t.DOC END"),
                () -> "expected TO_CLOB CASE WHEN:\n" + merge);
    }

    @Test
    void merge_emits_systimestamp_for_updt_audit_columns() {
        String merge = sql.buildMerge(t);
        assertTrue(merge.contains("UPDT_TS = SYSTIMESTAMP"),
                () -> "expected SYSTIMESTAMP for UPDT_TS:\n" + merge);
    }

    @Test
    void merge_skips_crte_ts_in_update_but_inserts_it() {
        String merge = sql.buildMerge(t);
        // No CRTE_TS = ... in UPDATE SET
        int updateStart = merge.indexOf("WHEN MATCHED THEN UPDATE SET");
        int insertStart = merge.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateSection = merge.substring(updateStart, insertStart);
        assertFalse(updateSection.contains("CRTE_TS ="),
                "CRTE_TS must not be in UPDATE SET");
        // But CRTE_TS IS in the INSERT column list with SYSTIMESTAMP value
        String insertSection = merge.substring(insertStart);
        assertTrue(insertSection.contains("CRTE_TS"));
        assertTrue(insertSection.contains("SYSTIMESTAMP"));
    }

    @Test
    void merge_skips_insert_only_columns_in_update() {
        String merge = sql.buildMerge(t);
        int updateStart = merge.indexOf("WHEN MATCHED THEN UPDATE SET");
        int insertStart = merge.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateSection = merge.substring(updateStart, insertStart);
        assertFalse(updateSection.contains("CRTE_USER_NAM"),
                "insertOnly columns must not be in UPDATE SET");
        assertTrue(merge.substring(insertStart).contains(":crteUserNam"),
                "insertOnly columns must appear in INSERT VALUES");
    }

    @Test
    void merge_skips_pk_in_update_set() {
        String merge = sql.buildMerge(t);
        int updateStart = merge.indexOf("WHEN MATCHED THEN UPDATE SET");
        int insertStart = merge.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateSection = merge.substring(updateStart, insertStart);
        assertFalse(updateSection.contains("ID ="), "PK must not appear in UPDATE SET");
    }

    @Test
    void select_by_pk_uses_pk_json_name_as_named_param() {
        assertEquals("SELECT ID, NAME, DOC, ACTIVE, CRTE_USER_NAM, CRTE_TS, UPDT_TS "
                + "FROM APP.T1 WHERE ID = :id", sql.buildSelectByPk(t));
    }

    @Test
    void select_by_fk_uses_fk_value_named_param() {
        String sqlText = sql.buildSelectByFk(t, "PARENT_ID");
        assertTrue(sqlText.contains("FROM APP.T1"));
        assertTrue(sqlText.contains("WHERE PARENT_ID = :fkValue"));
    }

    @Test
    void merge_otherAuditColumn_omittedFromUpdateSet() {
        TableMetadata withOtherAudit = TableMetadata.builder()
                .name("X").alias("x").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        // Audit column that is NOT one of CRTE_TS / UPDT_TS / RPLCTN_UPDT_TS
                        ColumnMetadata.builder().jsonName("syncTs").dbColumn("SYNC_TS").sqlType("TIMESTAMP")
                                .audit(true).build(),
                        ColumnMetadata.builder().jsonName("name").dbColumn("NAME").sqlType("VARCHAR").build()))
                .build();
        withOtherAudit.validate();
        String merge = sql.buildMerge(withOtherAudit);
        int updateStart = merge.indexOf("WHEN MATCHED THEN UPDATE SET");
        int insertStart = merge.indexOf("WHEN NOT MATCHED THEN INSERT");
        String updateSection = merge.substring(updateStart, insertStart);
        // Other audit columns fall into the "Optional.empty()" branch → not in UPDATE SET
        assertFalse(updateSection.contains("SYNC_TS ="),
                "other audit columns must not appear in UPDATE SET");
        // But the audit column IS in INSERT with SYSTIMESTAMP
        assertTrue(merge.substring(insertStart).contains("SYNC_TS"));
    }

    @Test
    void merge_nonAuditNonClobNonNullGuard_emitsDirectAssignment() {
        TableMetadata bare = TableMetadata.builder()
                .name("Y").alias("y").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        // nullGuard:false, non-CLOB, non-audit → direct assignment branch
                        ColumnMetadata.builder().jsonName("status").dbColumn("STATUS").sqlType("VARCHAR")
                                .nullGuard(false).build()))
                .build();
        bare.validate();
        String merge = sql.buildMerge(bare);
        assertTrue(merge.contains("STATUS = :status"),
                () -> "expected direct assignment:\n" + merge);
        assertFalse(merge.contains("STATUS = COALESCE"));
    }

    @Test
    void merge_is_cached_between_calls() {
        // Same instance is returned both times — proves the ConcurrentHashMap cache works
        assertSame(sql.buildMerge(t), sql.buildMerge(t));
    }

    @Test
    void no_orphan_children_with_no_child_list() {
        TableMetadata bare = TableMetadata.builder()
                .name("X").alias("x").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder().jsonName("id").dbColumn("ID")
                        .sqlType("VARCHAR").pk(true).nullGuard(false).build()))
                .children(List.of(ChildMetadata.builder().build())) // child not used here
                .build();
        bare.setChildren(List.of()); // wipe
        assertDoesNotThrow(() -> sql.buildMerge(bare));
    }
}
