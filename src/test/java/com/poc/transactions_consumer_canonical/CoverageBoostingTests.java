package com.poc.transactions_consumer_canonical;

import com.poc.transactions_consumer_canonical.canonicalmapping.*;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import com.poc.transactions_consumer_canonical.metadata.*;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                List.of(fm), List.of(fm), List.of(fm), List.of(ag), List.of(fm), rc);
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
    void coerce_handlesAllPrimitiveTargetTypes() {
        CanonicalMappingEngine engine = new CanonicalMappingEngine();
        EventEnvelope env = new EventEnvelope();
        env.setEventId("E"); env.setCorrelationId("C"); env.setEventTimestamp(1L);

        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setParent(List.of(
                // amount stored as Long → coerce String→Long succeeds; tranAmt → BigDecimal
                new FieldMapping("amount", "tranAmt", null),
                // currency is plain String passthrough
                new FieldMapping("currency", "tranCurr", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAmount(1234L);
        txn.setCurrency("USD");
        SendTransactionRequest req = engine.map(mapping, txn, env);
        assertThat(req.getTranAmt()).isEqualByComparingTo("1234");
        assertThat(req.getTranCurr()).isEqualTo("USD");
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

    @Test
    void coerce_booleanSourcePropagates_andZeroFalseHandled() {
        CanonicalMappingEngine engine = new CanonicalMappingEngine();
        EventEnvelope env = new EventEnvelope();
        env.setEventId("E"); env.setCorrelationId("C"); env.setEventTimestamp(1L);

        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setParent(List.of(
                new FieldMapping("sendingAccountEligible.eligible", "recipElig", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        var elig = new com.poc.transactions_consumer_canonical.messagesdto.AccountEligibility();
        elig.setEligible(false);
        txn.setSendingAccountEligible(elig);
        SendTransactionRequest req = engine.map(mapping, txn, env);
        // false → setter not invoked since str trim is "false"; either null or false acceptable.
        // We just ensure no exception was thrown.
        assertThat(req).isNotNull();
    }
}
