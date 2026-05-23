package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.mapper.SendTranAddrDtlRowMapper;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SendTranAddrDtlRepositoryImpl implements SendTranAddrDtlRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTranAddrDtlRowMapper rowMapper;
    private final SqlQueries sqlQueries;

    @Override
    public void mergeAll(List<SendTranAddrDtl> addresses) {
        if (addresses == null || addresses.isEmpty()) return;
        log.debug("MERGE SEND_TRAN_ADDR_DTL batch size={} for tranId={}", addresses.size(), addresses.get(0).getTranId());
        SqlParameterSource[] batch = addresses.stream()
                .map(this::toParams)
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(sqlQueries.sendTranAddrDtlMerge(), batch);
        log.debug("MERGE SEND_TRAN_ADDR_DTL complete");
    }

    @Override
    public List<SendTranAddrDtl> findByTranId(String tranId) {
        log.debug("SELECT SEND_TRAN_ADDR_DTL tranId={}", tranId);
        return jdbc.query(sqlQueries.sendTranAddrDtlSelectByTranId(),
                new MapSqlParameterSource("tranId", tranId), rowMapper);
    }

    @Override
    public void deleteByTranId(String tranId) {
        log.debug("DELETE SEND_TRAN_ADDR_DTL tranId={}", tranId);
        int rows = jdbc.update(sqlQueries.sendTranAddrDtlDeleteByTranId(), new MapSqlParameterSource("tranId", tranId));
        log.debug("DELETE SEND_TRAN_ADDR_DTL removed {} rows for tranId={}", rows, tranId);
    }

    @Override
    public void deleteByTranIdNotIn(String tranId, List<String> keepIds) {
        log.debug("DELETE SEND_TRAN_ADDR_DTL obsolete addresses tranId={} keepCount={}", tranId, keepIds.size());
        int rows = jdbc.update(sqlQueries.sendTranAddrDtlDeleteByTranIdNotIn(),
                new MapSqlParameterSource("tranId", tranId).addValue("ids", keepIds));
        log.debug("DELETE SEND_TRAN_ADDR_DTL removed {} obsolete rows for tranId={}", rows, tranId);
    }

    private MapSqlParameterSource toParams(SendTranAddrDtl a) {
        return new MapSqlParameterSource()
                .addValue("id",           a.getId(),           Types.VARCHAR)
                .addValue("tranId",       a.getTranId(),       Types.VARCHAR)
                .addValue("addrType",     a.getAddrType(),     Types.VARCHAR)
                .addValue("stLine1",      a.getStLine1(),      Types.VARCHAR)
                .addValue("stLine2",      a.getStLine2(),      Types.VARCHAR)
                .addValue("city",         a.getCity(),         Types.VARCHAR)
                .addValue("st",           a.getSt(),           Types.VARCHAR)
                .addValue("cntryNam",     a.getCntryNam(),     Types.VARCHAR)
                .addValue("postCd",       a.getPostCd(),       Types.VARCHAR)
                .addValue("addrStat",     a.getAddrStat(),     Types.VARCHAR)
                .addValue("postCdStat",   a.getPostCdStat(),   Types.VARCHAR)
                .addValue("crteUserNam",  a.getCrteUserNam(),  Types.VARCHAR)
                .addValue("updtUserNam",  a.getUpdtUserNam(),  Types.VARCHAR);
    }
}
