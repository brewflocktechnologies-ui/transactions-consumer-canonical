package com.poc.transactions_consumer_canonical.repository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SqlQueries} — verifies the schema-token rewrite logic.
 * Avoids @SpringBootTest by injecting field values via reflection.
 */
class SqlQueriesTest {

    private SqlQueries withSchemaAndTemplate(String schema, String template) {
        SqlQueries q = new SqlQueries();
        ReflectionTestUtils.setField(q, "schemaOwner", schema);
        // Reuse one accessor to verify token rewrite — pick sendTranDtlUpsert
        ReflectionTestUtils.setField(q, "sendTranDtlUpsert", template);
        return q;
    }

    @Test
    void replacesDefaultSchemaToken_withConfiguredSchema() {
        SqlQueries q = withSchemaAndTemplate("MY_SCHEMA",
                "MERGE INTO SEND_TXN_OWNER.SEND_TRAN_DTL t USING ...");
        assertThat(q.sendTranDtlUpsert()).startsWith("MERGE INTO MY_SCHEMA.SEND_TRAN_DTL");
    }

    @Test
    void blankSchema_stripsTokenAltogether() {
        SqlQueries q = withSchemaAndTemplate("",
                "MERGE INTO SEND_TXN_OWNER.SEND_TRAN_DTL t USING ...");
        assertThat(q.sendTranDtlUpsert()).startsWith("MERGE INTO SEND_TRAN_DTL");
    }

    @Test
    void nullSchema_stripsTokenAltogether() {
        SqlQueries q = withSchemaAndTemplate(null,
                "MERGE INTO SEND_TXN_OWNER.SEND_TRAN_DTL t USING ...");
        assertThat(q.sendTranDtlUpsert()).startsWith("MERGE INTO SEND_TRAN_DTL");
    }

    @Test
    void allAccessors_rewriteSchema() {
        SqlQueries q = new SqlQueries();
        ReflectionTestUtils.setField(q, "schemaOwner", "S");
        String template = "SEND_TXN_OWNER.X";
        for (String field : new String[]{
                "sendTranDtlUpsert", "sendTranDtlSelectByTranId",
                "sendRecipDtlUpsert", "sendRecipDtlSelectByTranId",
                "sendTranAddrDtlMerge", "sendTranAddrDtlSelectByTranId",
                "sendTranAddrDtlDeleteByTranId", "sendTranAddrDtlDeleteByTranIdNotIn"}) {
            ReflectionTestUtils.setField(q, field, template);
        }
        assertThat(q.sendTranDtlUpsert()).isEqualTo("S.X");
        assertThat(q.sendTranDtlSelectByTranId()).isEqualTo("S.X");
        assertThat(q.sendRecipDtlUpsert()).isEqualTo("S.X");
        assertThat(q.sendRecipDtlSelectByTranId()).isEqualTo("S.X");
        assertThat(q.sendTranAddrDtlMerge()).isEqualTo("S.X");
        assertThat(q.sendTranAddrDtlSelectByTranId()).isEqualTo("S.X");
        assertThat(q.sendTranAddrDtlDeleteByTranId()).isEqualTo("S.X");
        assertThat(q.sendTranAddrDtlDeleteByTranIdNotIn()).isEqualTo("S.X");
    }
}
