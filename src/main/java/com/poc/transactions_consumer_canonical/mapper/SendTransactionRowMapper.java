package com.poc.transactions_consumer_canonical.mapper;

import com.poc.transactions_consumer_canonical.model.SendTransaction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class SendTransactionRowMapper implements RowMapper<SendTransaction> {

    @Override
    public SendTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        Integer nonFinTxnRaw = rs.getObject("NON_FIN_TXN", Integer.class);
        return SendTransaction.builder()
                .tranId(rs.getString("TRAN_ID"))
                .tranInitId(rs.getString("TRAN_INIT_ID"))
                .origInstId(rs.getString("ORIG_INST_ID"))
                .origInstNam(rs.getString("ORIG_INST_NAM"))
                .tranfrAcptNam(rs.getString("TRANFR_ACPT_NAM"))
                .tranfrAcptId(rs.getString("TRANFR_ACPT_ID"))
                .tranCrteDt(toLocalDateTime(rs, "TRAN_CRTE_DT"))
                .tranType(rs.getString("TRAN_TYPE"))
                .custRefNum(rs.getString("CUST_REF_NUM"))
                .curStat(rs.getString("CUR_STAT"))
                .origStat(rs.getString("ORIG_STAT"))
                .useCase(rs.getString("USE_CASE"))
                .msgType(rs.getString("MSG_TYPE"))
                .refId(rs.getString("REF_ID"))
                .swSerNum(rs.getString("SW_SER_NUM"))
                .bnkntRefNum(rs.getString("BNKNT_REF_NUM"))
                .sendAcct(rs.getString("SEND_ACCT"))
                .recipAcct(rs.getString("RECIP_ACCT"))
                .tranAmt(rs.getBigDecimal("TRAN_AMT"))
                .tranCurr(rs.getString("TRAN_CURR"))
                .errCd(rs.getString("ERR_CD"))
                .fundAvail(rs.getString("FUND_AVAIL"))
                .corltnId(rs.getString("CORLTN_ID"))
                .crteTs(toLocalDateTime(rs, "CRTE_TS"))
                .crteUserNam(rs.getString("CRTE_USER_NAM"))
                .updtTs(toLocalDateTime(rs, "UPDT_TS"))
                .updtUserNam(rs.getString("UPDT_USER_NAM"))
                .rplctnUpdtTs(toLocalDateTime(rs, "RPLCTN_UPDT_TS"))
                .ntwrkCd(rs.getString("NTWRK_CD"))
                .ntwrkRespCd(rs.getString("NTWRK_RESP_CD"))
                .tranInitNam(rs.getString("TRAN_INIT_NAM"))
                .namStat(rs.getString("NAM_STAT"))
                .cvcStat(rs.getString("CVC_STAT"))
                .cvcRespCd(rs.getString("CVC_RESP_CD"))
                .acctNum(rs.getString("ACCT_NUM"))
                .acctType(rs.getString("ACCT_TYPE"))
                .acctHoldNam(rs.getString("ACCT_HOLD_NAM"))
                .errCdDesc(rs.getString("ERR_CD_DESC"))
                // Map NUMBER(1) → Boolean; null DB value yields null (field is nullable in model)
                .nonFinTxn(nonFinTxnRaw != null ? nonFinTxnRaw == 1 : null)
                .ntwrkRespCdDesc(rs.getString("NTWRK_RESP_CD_DESC"))
                .build();
    }

    /**
     * Reads a TIMESTAMP (or TIMESTAMP WITH TIME ZONE) column as UTC LocalDateTime.
     * Using {@code Timestamp.toInstant()} avoids the JVM-default-timezone pitfall
     * of the legacy {@code Timestamp.toLocalDateTime()} method.
     */
    private LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime() : null;
    }
}
