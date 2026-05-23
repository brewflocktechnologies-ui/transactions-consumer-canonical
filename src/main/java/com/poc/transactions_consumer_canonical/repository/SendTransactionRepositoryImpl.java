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

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTransactionRowMapper rowMapper;
    private final SqlQueries sqlQueries;

    @Override
    public void upsert(SendTransaction t) {
        log.debug("MERGE SEND_TRANSACTIONS tranId={}", t.getTranId());
        jdbc.update(sqlQueries.sendTransactionsUpsert(), toParams(t));
        log.debug("MERGE SEND_TRANSACTIONS complete tranId={}", t.getTranId());
    }

    @Override
    public Optional<SendTransaction> findById(String tranId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sqlQueries.sendTransactionsSelectById(),
                            new MapSqlParameterSource("tranId", tranId), rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SendTransaction> findAll(int offset, int size) {
        return jdbc.query(sqlQueries.sendTransactionsSelectPage(),
                new MapSqlParameterSource("offset", offset).addValue("size", size),
                rowMapper);
    }

    @Override
    public long count() {
        Long result = jdbc.queryForObject(sqlQueries.sendTransactionsCount(), new MapSqlParameterSource(), Long.class);
        return result != null ? result : 0L;
    }

    @Override
    public boolean deleteById(String tranId) {
        return jdbc.update(sqlQueries.sendTransactionsDelete(), new MapSqlParameterSource("tranId", tranId)) > 0;
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
