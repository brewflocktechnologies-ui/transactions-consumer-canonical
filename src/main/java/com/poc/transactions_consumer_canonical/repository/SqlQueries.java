package com.poc.transactions_consumer_canonical.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:sql/queries.properties")
public class SqlQueries {

    private static final String DEFAULT_SCHEMA_TOKEN = "SEND_TXN_OWNER.";

    @Value("${app.db.schema:SEND_TXN_OWNER}")
    private String schemaOwner;

    // NOTE: SEND_TRANSACTIONS SQL is fully metadata-driven (see SendTransactionRepositoryImpl
    // + SqlBuilder). No static properties needed here for that table.

    @Value("${sql.send-tran-dtl.upsert}")
    private String sendTranDtlUpsert;

    @Value("${sql.send-tran-dtl.select-by-tran-id}")
    private String sendTranDtlSelectByTranId;

    @Value("${sql.send-tran-dtl.delete}")
    private String sendTranDtlDelete;

    @Value("${sql.send-recip-dtl.upsert}")
    private String sendRecipDtlUpsert;

    @Value("${sql.send-recip-dtl.select-by-tran-id}")
    private String sendRecipDtlSelectByTranId;

    @Value("${sql.send-recip-dtl.delete}")
    private String sendRecipDtlDelete;

    @Value("${sql.send-tran-addr-dtl.merge}")
    private String sendTranAddrDtlMerge;

    @Value("${sql.send-tran-addr-dtl.select-by-tran-id}")
    private String sendTranAddrDtlSelectByTranId;

    @Value("${sql.send-tran-addr-dtl.delete-by-tran-id}")
    private String sendTranAddrDtlDeleteByTranId;

    @Value("${sql.send-tran-addr-dtl.delete-by-tran-id-not-in}")
    private String sendTranAddrDtlDeleteByTranIdNotIn;

    public String sendTranDtlUpsert() {
        return withSchema(sendTranDtlUpsert);
    }

    public String sendTranDtlSelectByTranId() {
        return withSchema(sendTranDtlSelectByTranId);
    }

    public String sendTranDtlDelete() {
        return withSchema(sendTranDtlDelete);
    }

    public String sendRecipDtlUpsert() {
        return withSchema(sendRecipDtlUpsert);
    }

    public String sendRecipDtlSelectByTranId() {
        return withSchema(sendRecipDtlSelectByTranId);
    }

    public String sendRecipDtlDelete() {
        return withSchema(sendRecipDtlDelete);
    }

    public String sendTranAddrDtlMerge() {
        return withSchema(sendTranAddrDtlMerge);
    }

    public String sendTranAddrDtlSelectByTranId() {
        return withSchema(sendTranAddrDtlSelectByTranId);
    }

    public String sendTranAddrDtlDeleteByTranId() {
        return withSchema(sendTranAddrDtlDeleteByTranId);
    }

    public String sendTranAddrDtlDeleteByTranIdNotIn() {
        return withSchema(sendTranAddrDtlDeleteByTranIdNotIn);
    }

    private String withSchema(String sql) {
        return sql.replace(DEFAULT_SCHEMA_TOKEN, schemaOwnerPrefix());
    }

    private String schemaOwnerPrefix() {
        return (schemaOwner != null && !schemaOwner.isBlank()) ? schemaOwner + "." : "";
    }
}
