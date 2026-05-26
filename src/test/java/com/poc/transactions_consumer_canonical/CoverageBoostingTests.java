package com.poc.transactions_consumer_canonical;

import com.poc.transactions_consumer_canonical.canonicalmapping.*;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.metadata.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverageBoostingTests {

    @Test
    void dtoConstructorsAndAccessors() {
        FieldMapping fm = new FieldMapping("a", "b", "c");
        assertThat(fm.getSource()).isEqualTo("a");
        fm.setSource("x");
        assertThat(fm.getSource()).isEqualTo("x");

        AddrDtlGroup ag = new AddrDtlGroup("SENDER", List.of(fm));
        assertThat(ag.getAddrType()).isEqualTo("SENDER");

        RulesConfig rc = new RulesConfig(List.of("A"), List.of("U"));
        assertThat(rc.getAllowedEventSources()).contains("A");

        EventTypeMapping etm = new EventTypeMapping("ET", List.of("e1"), "tran", "T",
                List.of(fm), List.of(fm), List.of(fm), List.of(ag), List.of(fm), null, rc, null);
        assertThat(etm.getEventType()).isEqualTo("ET");
        assertThat(etm.toString()).contains("ET");
    }

    @Test
    void tableAndChildMetadata_basicLookupsAndQualifiedName() {
        ChildMetadata child = ChildMetadata.builder()
                .jsonName("child")
                .tableRef("CHILD_TABLE")
                .cardinality("ONE_TO_ONE")
                .childKey("PARENT_ID")
                .build();
        assertThat(child.isOneToOne()).isTrue();
        assertThat(child.isOneToMany()).isFalse();
        assertThat(child.isGenerateIdIfMissing()).isFalse();
        child.setGenerateIdIfMissing(true);
        assertThat(child.isGenerateIdIfMissing()).isTrue();

        TableMetadata table = TableMetadata.builder()
                .alias("t").name("TBL").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build(),
                        ColumnMetadata.builder().dbColumn("NAME").jsonName("name").sqlType("VARCHAR").build()))
                .build();
        table.validate();
        assertThat(table.pkColumn().getDbColumn()).isEqualTo("ID");
        assertThat(table.qualifiedName()).isEqualTo("TBL");
        table.setSchema("SC");
        assertThat(table.qualifiedName()).isEqualTo("SC.TBL");
        assertThat(table.columnByJsonName("name")).isPresent();
        assertThat(table.columnByJsonName("doesnotexist")).isEmpty();
        assertThat(table.jsonNameForColumn("NAME")).isEqualTo("name");
    }

    // ────────────────────────────────────────────────────────────────
    // TableMetadata.validate() — exhaustive error branches
    // ────────────────────────────────────────────────────────────────

    @Test
    void validate_missingName() {
        TableMetadata t = TableMetadata.builder()
                .alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'name'");
    }

    @Test
    void validate_missingAlias() {
        TableMetadata t = TableMetadata.builder()
                .name("T").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'alias'");
    }

    @Test
    void validate_missingPk() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'pk'");
    }

    @Test
    void validate_missingPkJsonName() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'pkJsonName'");
    }

    @Test
    void validate_noColumns() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of())
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("no columns");
    }

    @Test
    void validate_columnMissingDbColumn() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing dbColumn");
    }

    @Test
    void validate_columnMissingSqlType() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing sqlType");
    }

    @Test
    void validate_duplicateJsonName() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build(),
                        ColumnMetadata.builder().dbColumn("A").jsonName("dup").sqlType("VARCHAR").build(),
                        ColumnMetadata.builder().dbColumn("B").jsonName("dup").sqlType("VARCHAR").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("duplicate jsonName");
    }

    @Test
    void validate_childMissingTableRef() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .children(List.of(ChildMetadata.builder().jsonName("c").cardinality("ONE_TO_ONE")
                        .childKey("TRAN_ID").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing tableRef");
    }

    @Test
    void validate_childInvalidCardinality() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .children(List.of(ChildMetadata.builder().jsonName("c").tableRef("REF")
                        .cardinality("MANY_TO_MANY").childKey("TRAN_ID").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("invalid cardinality");
    }

    @Test
    void validate_childMissingChildKey() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .children(List.of(ChildMetadata.builder().jsonName("c").tableRef("REF")
                        .cardinality("ONE_TO_MANY").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing childKey");
    }

    @Test
    void validate_oneToManyChild_succeeds() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .children(List.of(ChildMetadata.builder().jsonName("c").tableRef("REF")
                        .cardinality("ONE_TO_MANY").childKey("TRAN_ID").build()))
                .build();
        t.validate();
    }

    // ────────────────────────────────────────────────────────────────
    // CanonicalMappingEngine — coerce branches
    // ────────────────────────────────────────────────────────────────

    @Test
    void map_writesSourceValuesVerbatimToTargetKeys() {
        CanonicalMappingEngine engine = new CanonicalMappingEngine();
        EventEnvelope env = new EventEnvelope();
        env.setEventId("E"); env.setCorrelationId("C"); env.setEventTimestamp(1L);

        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("amount",   "tranAmt",   null),
                new FieldMapping("currency", "tranCurr",  null)
        ));
        Map<String, Object> txn = CaseInsensitiveJsonMap.wrapMap(Map.of(
                "amount",   "1234",
                "currency", "USD"));
        Map<String, Object> req = engine.map(mapping, txn, env);
        assertThat(req)
                .containsEntry("tranAmt", "1234")
                .containsEntry("tranCurr", "USD");
    }

    @Test
    void tableMetadata_validate_skipsNullChildrenList() {
        // Builder default is empty list; manually null it out to hit the
        // `children == null → return` branch in validateChildren().
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        t.setChildren(null);
        t.validate();
        assertThat(t.getChildren()).isNull();
    }

    // ────────────────────────────────────────────────────────────────
    // TableMetadata.validate() — isBlank() branches (non-null but blank)
    // ────────────────────────────────────────────────────────────────

    @Test
    void validate_blankName() {
        TableMetadata t = TableMetadata.builder()
                .name("  ").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'name'");
    }

    @Test
    void validate_blankAlias() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("  ").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'alias'");
    }

    @Test
    void validate_blankPk() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("  ").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'pk'");
    }

    @Test
    void validate_blankPkJsonName() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("  ")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing 'pkJsonName'");
    }

    @Test
    void validate_columnBlankDbColumn() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build(),
                        ColumnMetadata.builder().dbColumn("  ").jsonName("name").sqlType("VARCHAR").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing dbColumn");
    }

    @Test
    void validate_columnBlankSqlType() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").sqlType("  ").pk(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("missing sqlType");
    }

    @Test
    void validate_clobAndNullGuardConflict() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build(),
                        ColumnMetadata.builder().dbColumn("DOC").jsonName("doc").sqlType("CLOB")
                                .clob(true).nullGuard(true).build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("clob:true requires nullGuard:false");
    }

    @Test
    void qualifiedName_blankSchema_returnsJustName() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        t.setSchema("  "); // blank, not null → should behave like no schema
        assertThat(t.qualifiedName()).isEqualTo("T");
    }

    @Test
    void validate_oneToOneChild_succeeds() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .children(List.of(ChildMetadata.builder().jsonName("c").tableRef("REF")
                        .cardinality("ONE_TO_ONE").childKey("TRAN_ID").build()))
                .build();
        t.validate(); // should not throw
    }

    @Test
    void validate_duplicateDbColumn() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build(),
                        ColumnMetadata.builder().dbColumn("ID").jsonName("id2").sqlType("VARCHAR").build()))
                .build();
        assertThatThrownBy(t::validate).hasMessageContaining("duplicate dbColumn");
    }

    @Test
    void validate_nullColumns_throwsNoColumns() {
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("ID").pkJsonName("id")
                .build();
        t.setColumns(null);
        assertThatThrownBy(t::validate).hasMessageContaining("no columns");
    }

    @Test
    void validate_pkColumnNameMismatch_throwsOnPkColumn() {
        // PK column exists with pk:true, but table.pk points to a different name
        TableMetadata t = TableMetadata.builder()
                .name("T").alias("t").pk("WRONG_COL").pkJsonName("id")
                .columns(List.of(ColumnMetadata.builder()
                        .dbColumn("ID").jsonName("id").sqlType("VARCHAR").pk(true).build()))
                .build();
        // pkFound will be true (ID has pk:true), but pkColumn() will throw because WRONG_COL not found
        assertThatThrownBy(t::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void map_booleanSourcePropagatesThroughNestedPath() {
        CanonicalMappingEngine engine = new CanonicalMappingEngine();
        EventEnvelope env = new EventEnvelope();
        env.setEventId("E"); env.setCorrelationId("C"); env.setEventTimestamp(1L);

        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("sendingAccountEligible.eligible", "recipElig", null)
        ));
        Map<String, Object> txn = CaseInsensitiveJsonMap.wrapMap(
                Map.of("sendingAccountEligible", Map.of("eligible", false)));
        Map<String, Object> req = engine.map(mapping, txn, env);
        assertThat(req).containsEntry("recipElig", false);
    }
}
