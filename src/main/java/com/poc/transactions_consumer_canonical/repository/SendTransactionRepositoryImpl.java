package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.mapper.SendTransactionRowMapper;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SendTransactionRepositoryImpl implements SendTransactionRepository {

    private static final String UPSERT = """
            MERGE INTO SEND_TXN_OWNER.SEND_TRANSACTIONS t
            USING (SELECT :tranId AS TRAN_ID FROM DUAL) src
            ON (t.TRAN_ID = src.TRAN_ID)
            WHEN MATCHED THEN UPDATE SET
                TRAN_INIT_ID        = COALESCE(:tranInitId,       t.TRAN_INIT_ID),
                ORIG_INST_ID        = COALESCE(:origInstId,       t.ORIG_INST_ID),
                ORIG_INST_NAM       = COALESCE(:origInstNam,      t.ORIG_INST_NAM),
                TRANFR_ACPT_NAM     = COALESCE(:tranfrAcptNam,    t.TRANFR_ACPT_NAM),
                TRANFR_ACPT_ID      = COALESCE(:tranfrAcptId,     t.TRANFR_ACPT_ID),
                TRAN_CRTE_DT        = COALESCE(:tranCrteDt,       t.TRAN_CRTE_DT),
                TRAN_TYPE           = COALESCE(:tranType,         t.TRAN_TYPE),
                CUST_REF_NUM        = COALESCE(:custRefNum,       t.CUST_REF_NUM),
                CUR_STAT            = COALESCE(:curStat,          t.CUR_STAT),
                ORIG_STAT           = COALESCE(:origStat,         t.ORIG_STAT),
                USE_CASE            = COALESCE(:useCase,          t.USE_CASE),
                MSG_TYPE            = COALESCE(:msgType,          t.MSG_TYPE),
                REF_ID              = COALESCE(:refId,            t.REF_ID),
                SW_SER_NUM          = COALESCE(:swSerNum,         t.SW_SER_NUM),
                BNKNT_REF_NUM       = COALESCE(:bnkntRefNum,      t.BNKNT_REF_NUM),
                SEND_ACCT           = COALESCE(:sendAcct,         t.SEND_ACCT),
                RECIP_ACCT          = COALESCE(:recipAcct,        t.RECIP_ACCT),
                TRAN_AMT            = COALESCE(:tranAmt,          t.TRAN_AMT),
                TRAN_CURR           = COALESCE(:tranCurr,         t.TRAN_CURR),
                ERR_CD              = COALESCE(:errCd,            t.ERR_CD),
                FUND_AVAIL          = COALESCE(:fundAvail,        t.FUND_AVAIL),
                CORLTN_ID           = COALESCE(:corltnId,         t.CORLTN_ID),
                UPDT_TS             = SYSTIMESTAMP,
                UPDT_USER_NAM       = COALESCE(:updtUserNam,      t.UPDT_USER_NAM),
                RPLCTN_UPDT_TS      = SYSTIMESTAMP,
                NTWRK_CD            = COALESCE(:ntwrkCd,          t.NTWRK_CD),
                NTWRK_RESP_CD       = COALESCE(:ntwrkRespCd,      t.NTWRK_RESP_CD),
                TRAN_INIT_NAM       = COALESCE(:tranInitNam,      t.TRAN_INIT_NAM),
                NAM_STAT            = COALESCE(:namStat,          t.NAM_STAT),
                CVC_STAT            = COALESCE(:cvcStat,          t.CVC_STAT),
                CVC_RESP_CD         = COALESCE(:cvcRespCd,        t.CVC_RESP_CD),
                ACCT_NUM            = COALESCE(:acctNum,          t.ACCT_NUM),
                ACCT_TYPE           = COALESCE(:acctType,         t.ACCT_TYPE),
                ACCT_HOLD_NAM       = COALESCE(:acctHoldNam,      t.ACCT_HOLD_NAM),
                ERR_CD_DESC         = COALESCE(:errCdDesc,        t.ERR_CD_DESC),
                NON_FIN_TXN         = COALESCE(:nonFinTxn,        t.NON_FIN_TXN),
                NTWRK_RESP_CD_DESC  = COALESCE(:ntwrkRespCdDesc,  t.NTWRK_RESP_CD_DESC)
            WHEN NOT MATCHED THEN INSERT (
                TRAN_ID, TRAN_INIT_ID, ORIG_INST_ID, ORIG_INST_NAM, TRANFR_ACPT_NAM,
                TRANFR_ACPT_ID, TRAN_CRTE_DT, TRAN_TYPE, CUST_REF_NUM, CUR_STAT,
                ORIG_STAT, USE_CASE, MSG_TYPE, REF_ID, SW_SER_NUM, BNKNT_REF_NUM,
                SEND_ACCT, RECIP_ACCT, TRAN_AMT, TRAN_CURR, ERR_CD, FUND_AVAIL,
                CORLTN_ID, CRTE_TS, CRTE_USER_NAM, UPDT_USER_NAM, RPLCTN_UPDT_TS,
                NTWRK_CD, NTWRK_RESP_CD, TRAN_INIT_NAM, NAM_STAT, CVC_STAT,
                CVC_RESP_CD, ACCT_NUM, ACCT_TYPE, ACCT_HOLD_NAM, ERR_CD_DESC,
                NON_FIN_TXN, NTWRK_RESP_CD_DESC
            ) VALUES (
                :tranId, :tranInitId, :origInstId, :origInstNam, :tranfrAcptNam,
                :tranfrAcptId, :tranCrteDt, :tranType, :custRefNum, :curStat,
                :origStat, :useCase, :msgType, :refId, :swSerNum, :bnkntRefNum,
                :sendAcct, :recipAcct, :tranAmt, :tranCurr, :errCd, :fundAvail,
                :corltnId, SYSTIMESTAMP, :crteUserNam, :updtUserNam, SYSTIMESTAMP,
                :ntwrkCd, :ntwrkRespCd, :tranInitNam, :namStat, :cvcStat,
                :cvcRespCd, :acctNum, :acctType, :acctHoldNam, :errCdDesc,
                :nonFinTxn, :ntwrkRespCdDesc
            )
            """;

    private static final String SELECT_BY_ID = """
            SELECT TRAN_ID, TRAN_INIT_ID, ORIG_INST_ID, ORIG_INST_NAM, TRANFR_ACPT_NAM,
                   TRANFR_ACPT_ID, TRAN_CRTE_DT, TRAN_TYPE, CUST_REF_NUM, CUR_STAT,
                   ORIG_STAT, USE_CASE, MSG_TYPE, REF_ID, SW_SER_NUM, BNKNT_REF_NUM,
                   SEND_ACCT, RECIP_ACCT, TRAN_AMT, TRAN_CURR, ERR_CD, FUND_AVAIL,
                   CORLTN_ID, CRTE_TS, CRTE_USER_NAM, UPDT_TS, UPDT_USER_NAM,
                   RPLCTN_UPDT_TS, NTWRK_CD, NTWRK_RESP_CD, TRAN_INIT_NAM, NAM_STAT,
                   CVC_STAT, CVC_RESP_CD, ACCT_NUM, ACCT_TYPE, ACCT_HOLD_NAM,
                   ERR_CD_DESC, NON_FIN_TXN, NTWRK_RESP_CD_DESC
            FROM SEND_TXN_OWNER.SEND_TRANSACTIONS
            WHERE TRAN_ID = :tranId
            """;

    private static final String SELECT_PAGE = """
            SELECT TRAN_ID, TRAN_INIT_ID, ORIG_INST_ID, ORIG_INST_NAM, TRANFR_ACPT_NAM,
                   TRANFR_ACPT_ID, TRAN_CRTE_DT, TRAN_TYPE, CUST_REF_NUM, CUR_STAT,
                   ORIG_STAT, USE_CASE, MSG_TYPE, REF_ID, SW_SER_NUM, BNKNT_REF_NUM,
                   SEND_ACCT, RECIP_ACCT, TRAN_AMT, TRAN_CURR, ERR_CD, FUND_AVAIL,
                   CORLTN_ID, CRTE_TS, CRTE_USER_NAM, UPDT_TS, UPDT_USER_NAM,
                   RPLCTN_UPDT_TS, NTWRK_CD, NTWRK_RESP_CD, TRAN_INIT_NAM, NAM_STAT,
                   CVC_STAT, CVC_RESP_CD, ACCT_NUM, ACCT_TYPE, ACCT_HOLD_NAM,
                   ERR_CD_DESC, NON_FIN_TXN, NTWRK_RESP_CD_DESC
            FROM SEND_TXN_OWNER.SEND_TRANSACTIONS
            ORDER BY TRAN_CRTE_DT DESC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM SEND_TXN_OWNER.SEND_TRANSACTIONS";

    private static final String DELETE = "DELETE FROM SEND_TXN_OWNER.SEND_TRANSACTIONS WHERE TRAN_ID = :tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTransactionRowMapper rowMapper;

    @Override
    public void upsert(SendTransaction t) {
        log.debug("MERGE SEND_TRANSACTIONS tranId={}", t.getTranId());
        jdbc.update(UPSERT, toParams(t));
        log.debug("MERGE SEND_TRANSACTIONS complete tranId={}", t.getTranId());
    }

    @Override
    public Optional<SendTransaction> findById(String tranId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(SELECT_BY_ID,
                            new MapSqlParameterSource("tranId", tranId), rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SendTransaction> findAll(int offset, int size) {
        return jdbc.query(SELECT_PAGE,
                new MapSqlParameterSource("offset", offset).addValue("size", size),
                rowMapper);
    }

    @Override
    public long count() {
        Long result = jdbc.queryForObject(COUNT, new MapSqlParameterSource(), Long.class);
        return result != null ? result : 0L;
    }

    @Override
    public boolean deleteById(String tranId) {
        return jdbc.update(DELETE, new MapSqlParameterSource("tranId", tranId)) > 0;
    }

    private MapSqlParameterSource toParams(SendTransaction t) {
        return new MapSqlParameterSource()
                .addValue("tranId",           t.getTranId(),            Types.VARCHAR)
                .addValue("tranInitId",        t.getTranInitId(),         Types.VARCHAR)
                .addValue("origInstId",        t.getOrigInstId(),         Types.VARCHAR)
                .addValue("origInstNam",       t.getOrigInstNam(),        Types.VARCHAR)
                .addValue("tranfrAcptNam",     t.getTranfrAcptNam(),      Types.VARCHAR)
                .addValue("tranfrAcptId",      t.getTranfrAcptId(),       Types.VARCHAR)
                .addValue("tranCrteDt",        t.getTranCrteDt(),         Types.TIMESTAMP)
                .addValue("tranType",          t.getTranType(),           Types.VARCHAR)
                .addValue("custRefNum",        t.getCustRefNum(),         Types.VARCHAR)
                .addValue("curStat",           t.getCurStat(),            Types.VARCHAR)
                .addValue("origStat",          t.getOrigStat(),           Types.VARCHAR)
                .addValue("useCase",           t.getUseCase(),            Types.VARCHAR)
                .addValue("msgType",           t.getMsgType(),            Types.VARCHAR)
                .addValue("refId",             t.getRefId(),              Types.VARCHAR)
                .addValue("swSerNum",          t.getSwSerNum(),           Types.VARCHAR)
                .addValue("bnkntRefNum",       t.getBnkntRefNum(),        Types.VARCHAR)
                .addValue("sendAcct",          t.getSendAcct(),           Types.VARCHAR)
                .addValue("recipAcct",         t.getRecipAcct(),          Types.VARCHAR)
                .addValue("tranAmt",           t.getTranAmt(),            Types.NUMERIC)
                .addValue("tranCurr",          t.getTranCurr(),           Types.VARCHAR)
                .addValue("errCd",             t.getErrCd(),              Types.VARCHAR)
                .addValue("fundAvail",         t.getFundAvail(),          Types.VARCHAR)
                .addValue("corltnId",          t.getCorltnId(),           Types.VARCHAR)
                .addValue("crteUserNam",       t.getCrteUserNam(),        Types.VARCHAR)
                .addValue("updtUserNam",       t.getUpdtUserNam(),        Types.VARCHAR)
                .addValue("ntwrkCd",           t.getNtwrkCd(),            Types.VARCHAR)
                .addValue("ntwrkRespCd",       t.getNtwrkRespCd(),        Types.VARCHAR)
                .addValue("tranInitNam",       t.getTranInitNam(),        Types.VARCHAR)
                .addValue("namStat",           t.getNamStat(),            Types.VARCHAR)
                .addValue("cvcStat",           t.getCvcStat(),            Types.VARCHAR)
                .addValue("cvcRespCd",         t.getCvcRespCd(),          Types.VARCHAR)
                .addValue("acctNum",           t.getAcctNum(),            Types.VARCHAR)
                .addValue("acctType",          t.getAcctType(),           Types.VARCHAR)
                .addValue("acctHoldNam",       t.getAcctHoldNam(),        Types.VARCHAR)
                .addValue("errCdDesc",         t.getErrCdDesc(),          Types.VARCHAR)
                // null → COALESCE preserves the existing DB value; true/false → writes 1/0
                .addValue("nonFinTxn",         t.getNonFinTxn() != null ? (t.getNonFinTxn() ? 1 : 0) : null, Types.NUMERIC)
                .addValue("ntwrkRespCdDesc",   t.getNtwrkRespCdDesc(),    Types.VARCHAR);
    }
}
