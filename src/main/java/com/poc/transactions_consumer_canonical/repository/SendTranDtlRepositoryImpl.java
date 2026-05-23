package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.mapper.SendTranDtlRowMapper;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SendTranDtlRepositoryImpl implements SendTranDtlRepository {

    private static final String UPSERT = """
            MERGE INTO SEND_TXN_OWNER.SEND_TRAN_DTL t
            USING (SELECT :tranId AS TRAN_ID FROM DUAL) src
            ON (t.TRAN_ID = src.TRAN_ID)
            WHEN MATCHED THEN UPDATE SET
                PAYMT_REF               = COALESCE(:paymtRef,            t.PAYMT_REF),
                UNQ_TRAN_REF            = COALESCE(:unqTranRef,          t.UNQ_TRAN_REF),
                ACQ_CNTRY_NAM           = COALESCE(:acqCntryNam,         t.ACQ_CNTRY_NAM),
                ACQ_ICA                 = COALESCE(:acqIca,              t.ACQ_ICA),
                FUND_SRC                = COALESCE(:fundSrc,             t.FUND_SRC),
                ICHG_RATE_DSGN          = COALESCE(:ichgRateDsgn,        t.ICHG_RATE_DSGN),
                MERCH_CAT_CD            = COALESCE(:merchCatCd,          t.MERCH_CAT_CD),
                PAYMT_TYPE              = COALESCE(:paymtType,           t.PAYMT_TYPE),
                POINT_SERV_INTRCTN      = COALESCE(:pointServIntrctn,    t.POINT_SERV_INTRCTN),
                TRAN_PRPS               = COALESCE(:tranPrps,            t.TRAN_PRPS),
                TRAN_SETL_AMT           = COALESCE(:tranSetlAmt,         t.TRAN_SETL_AMT),
                BNC_GTWY_RQST           = CASE WHEN :bncGtwyRqst  IS NOT NULL THEN TO_CLOB(:bncGtwyRqst)  ELSE t.BNC_GTWY_RQST  END,
                BNC_GTWY_RESP           = CASE WHEN :bncGtwyResp  IS NOT NULL THEN TO_CLOB(:bncGtwyResp)  ELSE t.BNC_GTWY_RESP  END,
                ORIG_RQST_PYLD          = CASE WHEN :origRqstPyld IS NOT NULL THEN TO_CLOB(:origRqstPyld) ELSE t.ORIG_RQST_PYLD END,
                ORIG_RESP_PYLD          = CASE WHEN :origRespPyld IS NOT NULL THEN TO_CLOB(:origRespPyld) ELSE t.ORIG_RESP_PYLD END,
                TRANFR_ACPT_ID          = COALESCE(:tranfrAcptId,        t.TRANFR_ACPT_ID),
                TRANFR_ACPT_NAM         = COALESCE(:tranfrAcptNam,       t.TRANFR_ACPT_NAM),
                MC_ASSGN_MERCH          = COALESCE(:mcAssgnMerch,        t.MC_ASSGN_MERCH),
                PAYMT_FACILTR_ID        = COALESCE(:paymtFacltrId,       t.PAYMT_FACILTR_ID),
                SUB_MERCH_ID            = COALESCE(:subMerchId,          t.SUB_MERCH_ID),
                TRANFR_TRML_ID          = COALESCE(:tranfrTrmlId,        t.TRANFR_TRML_ID),
                TRANFR_ACPT_ST_LINE1    = COALESCE(:tranfrAcptStLine1,   t.TRANFR_ACPT_ST_LINE1),
                TRANFR_ACPT_ST_LINE2    = COALESCE(:tranfrAcptStLine2,   t.TRANFR_ACPT_ST_LINE2),
                TRANFR_ACPT_CITY        = COALESCE(:tranfrAcptCity,      t.TRANFR_ACPT_CITY),
                TRANFR_ACPT_ST          = COALESCE(:tranfrAcptSt,        t.TRANFR_ACPT_ST),
                TRANFR_ACPT_CNTRY_NAM   = COALESCE(:tranfrAcptCntryNam,  t.TRANFR_ACPT_CNTRY_NAM),
                TRANFR_ACPT_POST_CD     = COALESCE(:tranfrAcptPostCd,    t.TRANFR_ACPT_POST_CD),
                UPDT_TS                 = SYSTIMESTAMP,
                UPDT_USER_NAM           = COALESCE(:updtUserNam,         t.UPDT_USER_NAM),
                EVENT_ID                = COALESCE(:eventId,             t.EVENT_ID),
                EVENT_TS                = COALESCE(:eventTs,             t.EVENT_TS),
                EVENT_CORLTN_ID         = COALESCE(:eventCorltnId,       t.EVENT_CORLTN_ID),
                MSG_VERSION             = COALESCE(:msgVersion,          t.MSG_VERSION),
                TRAN_CRTE_DT            = COALESCE(:tranCrteDt,          t.TRAN_CRTE_DT),
                RPLCTN_UPDT_TS          = SYSTIMESTAMP,
                TRAN_TYPE_IND_CD        = COALESCE(:tranTypeIndCd,       t.TRAN_TYPE_IND_CD),
                REGULATED_RATE_TYPE_CD  = COALESCE(:regulatedRateTypeCd, t.REGULATED_RATE_TYPE_CD),
                CVC_RESP_DESC           = COALESCE(:cvcRespDesc,         t.CVC_RESP_DESC),
                PROC_ID                 = COALESCE(:procId,              t.PROC_ID),
                ACQ_IDEN_CD             = COALESCE(:acqIdenCd,           t.ACQ_IDEN_CD),
                TRANFR_ACPT_MPG_ID      = COALESCE(:tranfrAcptMpgId,     t.TRANFR_ACPT_MPG_ID),
                TRANFR_ACPT_MERCH_VALUE = COALESCE(:tranfrAcptMerchValue, t.TRANFR_ACPT_MERCH_VALUE)
            WHEN NOT MATCHED THEN INSERT (
                TRAN_ID, PAYMT_REF, UNQ_TRAN_REF, ACQ_CNTRY_NAM, ACQ_ICA,
                FUND_SRC, ICHG_RATE_DSGN, MERCH_CAT_CD, PAYMT_TYPE, POINT_SERV_INTRCTN,
                TRAN_PRPS, TRAN_SETL_AMT, BNC_GTWY_RQST, BNC_GTWY_RESP, ORIG_RQST_PYLD,
                ORIG_RESP_PYLD, TRANFR_ACPT_ID, TRANFR_ACPT_NAM, MC_ASSGN_MERCH,
                PAYMT_FACILTR_ID, SUB_MERCH_ID, TRANFR_TRML_ID, TRANFR_ACPT_ST_LINE1,
                TRANFR_ACPT_ST_LINE2, TRANFR_ACPT_CITY, TRANFR_ACPT_ST, TRANFR_ACPT_CNTRY_NAM,
                TRANFR_ACPT_POST_CD, CRTE_TS, CRTE_USER_NAM, UPDT_USER_NAM, EVENT_ID,
                EVENT_TS, EVENT_CORLTN_ID, MSG_VERSION, TRAN_CRTE_DT, RPLCTN_UPDT_TS,
                TRAN_TYPE_IND_CD, REGULATED_RATE_TYPE_CD, CVC_RESP_DESC, PROC_ID,
                ACQ_IDEN_CD, TRANFR_ACPT_MPG_ID, TRANFR_ACPT_MERCH_VALUE
            ) VALUES (
                :tranId, :paymtRef, :unqTranRef, :acqCntryNam, :acqIca,
                :fundSrc, :ichgRateDsgn, :merchCatCd, :paymtType, :pointServIntrctn,
                :tranPrps, :tranSetlAmt, :bncGtwyRqst, :bncGtwyResp, :origRqstPyld,
                :origRespPyld, :tranfrAcptId, :tranfrAcptNam, :mcAssgnMerch,
                :paymtFacltrId, :subMerchId, :tranfrTrmlId, :tranfrAcptStLine1,
                :tranfrAcptStLine2, :tranfrAcptCity, :tranfrAcptSt, :tranfrAcptCntryNam,
                :tranfrAcptPostCd, SYSTIMESTAMP, :crteUserNam, :updtUserNam, :eventId,
                :eventTs, :eventCorltnId, :msgVersion, :tranCrteDt, SYSTIMESTAMP,
                :tranTypeIndCd, :regulatedRateTypeCd, :cvcRespDesc, :procId,
                :acqIdenCd, :tranfrAcptMpgId, :tranfrAcptMerchValue
            )
            """;

    private static final String SELECT_BY_TRAN_ID = """
            SELECT TRAN_ID, PAYMT_REF, UNQ_TRAN_REF, ACQ_CNTRY_NAM, ACQ_ICA, FUND_SRC,
                   ICHG_RATE_DSGN, MERCH_CAT_CD, PAYMT_TYPE, POINT_SERV_INTRCTN, TRAN_PRPS,
                   TRAN_SETL_AMT, BNC_GTWY_RQST, BNC_GTWY_RESP, ORIG_RQST_PYLD, ORIG_RESP_PYLD,
                   TRANFR_ACPT_ID, TRANFR_ACPT_NAM, MC_ASSGN_MERCH, PAYMT_FACILTR_ID, SUB_MERCH_ID,
                   TRANFR_TRML_ID, TRANFR_ACPT_ST_LINE1, TRANFR_ACPT_ST_LINE2, TRANFR_ACPT_CITY,
                   TRANFR_ACPT_ST, TRANFR_ACPT_CNTRY_NAM, TRANFR_ACPT_POST_CD, CRTE_TS, CRTE_USER_NAM,
                   UPDT_TS, UPDT_USER_NAM, EVENT_ID, EVENT_TS, EVENT_CORLTN_ID, MSG_VERSION,
                   TRAN_CRTE_DT, RPLCTN_UPDT_TS, TRAN_TYPE_IND_CD, REGULATED_RATE_TYPE_CD,
                   CVC_RESP_DESC, PROC_ID, ACQ_IDEN_CD, TRANFR_ACPT_MPG_ID, TRANFR_ACPT_MERCH_VALUE
            FROM SEND_TXN_OWNER.SEND_TRAN_DTL
            WHERE TRAN_ID = :tranId
            """;

    private static final String DELETE = "DELETE FROM SEND_TXN_OWNER.SEND_TRAN_DTL WHERE TRAN_ID = :tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTranDtlRowMapper rowMapper;

    @Override
    public void upsert(SendTranDtl d) {
        log.debug("MERGE SEND_TRAN_DTL tranId={}", d.getTranId());
        jdbc.update(UPSERT, toParams(d));
        log.debug("MERGE SEND_TRAN_DTL complete tranId={}", d.getTranId());
    }

    @Override
    public Optional<SendTranDtl> findByTranId(String tranId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(SELECT_BY_TRAN_ID,
                            new MapSqlParameterSource("tranId", tranId), rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteByTranId(String tranId) {
        jdbc.update(DELETE, new MapSqlParameterSource("tranId", tranId));
    }

    private MapSqlParameterSource toParams(SendTranDtl d) {
        return new MapSqlParameterSource()
                .addValue("tranId",               d.getTranId(),              Types.VARCHAR)
                .addValue("paymtRef",              d.getPaymtRef(),            Types.VARCHAR)
                .addValue("unqTranRef",            d.getUnqTranRef(),          Types.VARCHAR)
                .addValue("acqCntryNam",           d.getAcqCntryNam(),         Types.VARCHAR)
                .addValue("acqIca",                d.getAcqIca(),              Types.NUMERIC)
                .addValue("fundSrc",               d.getFundSrc(),             Types.VARCHAR)
                .addValue("ichgRateDsgn",          d.getIchgRateDsgn(),        Types.VARCHAR)
                .addValue("merchCatCd",            d.getMerchCatCd(),          Types.VARCHAR)
                .addValue("paymtType",             d.getPaymtType(),           Types.VARCHAR)
                .addValue("pointServIntrctn",      d.getPointServIntrctn(),    Types.VARCHAR)
                .addValue("tranPrps",              d.getTranPrps(),            Types.VARCHAR)
                .addValue("tranSetlAmt",           d.getTranSetlAmt(),         Types.NUMERIC)
                .addValue("bncGtwyRqst",           d.getBncGtwyRqst(),         Types.VARCHAR)
                .addValue("bncGtwyResp",           d.getBncGtwyResp(),         Types.VARCHAR)
                .addValue("origRqstPyld",          d.getOrigRqstPyld(),        Types.VARCHAR)
                .addValue("origRespPyld",          d.getOrigRespPyld(),        Types.VARCHAR)
                .addValue("tranfrAcptId",          d.getTranfrAcptId(),        Types.VARCHAR)
                .addValue("tranfrAcptNam",         d.getTranfrAcptNam(),       Types.VARCHAR)
                .addValue("mcAssgnMerch",          d.getMcAssgnMerch(),        Types.VARCHAR)
                .addValue("paymtFacltrId",         d.getPaymtFacltrId(),       Types.VARCHAR)
                .addValue("subMerchId",            d.getSubMerchId(),          Types.VARCHAR)
                .addValue("tranfrTrmlId",          d.getTranfrTrmlId(),        Types.VARCHAR)
                .addValue("tranfrAcptStLine1",     d.getTranfrAcptStLine1(),   Types.VARCHAR)
                .addValue("tranfrAcptStLine2",     d.getTranfrAcptStLine2(),   Types.VARCHAR)
                .addValue("tranfrAcptCity",        d.getTranfrAcptCity(),      Types.VARCHAR)
                .addValue("tranfrAcptSt",          d.getTranfrAcptSt(),        Types.VARCHAR)
                .addValue("tranfrAcptCntryNam",    d.getTranfrAcptCntryNam(),  Types.VARCHAR)
                .addValue("tranfrAcptPostCd",      d.getTranfrAcptPostCd(),    Types.VARCHAR)
                .addValue("crteUserNam",           d.getCrteUserNam(),         Types.VARCHAR)
                .addValue("updtUserNam",           d.getUpdtUserNam(),         Types.VARCHAR)
                .addValue("eventId",               d.getEventId(),             Types.VARCHAR)
                .addValue("eventTs",               d.getEventTs(),             Types.TIMESTAMP)
                .addValue("eventCorltnId",         d.getEventCorltnId(),       Types.VARCHAR)
                .addValue("msgVersion",            d.getMsgVersion(),          Types.VARCHAR)
                .addValue("tranCrteDt",            d.getTranCrteDt(),          Types.TIMESTAMP)
                .addValue("tranTypeIndCd",         d.getTranTypeIndCd(),       Types.VARCHAR)
                .addValue("regulatedRateTypeCd",   d.getRegulatedRateTypeCd(), Types.VARCHAR)
                .addValue("cvcRespDesc",           d.getCvcRespDesc(),         Types.VARCHAR)
                .addValue("procId",                d.getProcId(),              Types.VARCHAR)
                .addValue("acqIdenCd",             d.getAcqIdenCd(),           Types.VARCHAR)
                .addValue("tranfrAcptMpgId",       d.getTranfrAcptMpgId(),     Types.VARCHAR)
                .addValue("tranfrAcptMerchValue",  d.getTranfrAcptMerchValue(), Types.VARCHAR);
    }
}
