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

    private static final String TRAN_ID_PARAM = "tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTranDtlRowMapper rowMapper;
    private final SqlQueries sqlQueries;

    @Override
    public void upsert(SendTranDtl d) {
        log.debug("MERGE SEND_TRAN_DTL tranId={}", d.getTranId());
        jdbc.update(sqlQueries.sendTranDtlUpsert(), toParams(d));
        log.debug("MERGE SEND_TRAN_DTL complete tranId={}", d.getTranId());
    }

    @Override
    public Optional<SendTranDtl> findByTranId(String tranId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sqlQueries.sendTranDtlSelectByTranId(),
                            new MapSqlParameterSource(TRAN_ID_PARAM, tranId), rowMapper));
        } catch (EmptyResultDataAccessException _) {
            return Optional.empty();
        }
    }

    private MapSqlParameterSource toParams(SendTranDtl d) {
        return new MapSqlParameterSource()
                .addValue(TRAN_ID_PARAM,          d.getTranId(),              Types.VARCHAR)
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
                // TRAN_SETL_AMT is VARCHAR2(50) in the DDL — must bind as VARCHAR,
                // otherwise Oracle rejects COALESCE(:numericBind, t.varchar2Column) with ORA-00932.
                .addValue("tranSetlAmt",           d.getTranSetlAmt(),         Types.VARCHAR)
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
