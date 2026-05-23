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

    // 1:many child — strategy is MERGE per record (null guard) + delete removed IDs
    private static final String MERGE = """
            MERGE INTO SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL t
            USING (SELECT :id AS ID FROM DUAL) src
            ON (t.ID = src.ID)
            WHEN MATCHED THEN UPDATE SET
                ADDR_TYPE      = COALESCE(:addrType,   t.ADDR_TYPE),
                ST_LINE1       = COALESCE(:stLine1,    t.ST_LINE1),
                ST_LINE2       = COALESCE(:stLine2,    t.ST_LINE2),
                CITY           = COALESCE(:city,       t.CITY),
                ST             = COALESCE(:st,         t.ST),
                CNTRY_NAM      = COALESCE(:cntryNam,   t.CNTRY_NAM),
                POST_CD        = COALESCE(:postCd,     t.POST_CD),
                ADDR_STAT      = COALESCE(:addrStat,   t.ADDR_STAT),
                POST_CD_STAT   = COALESCE(:postCdStat, t.POST_CD_STAT),
                UPDT_USER_NAM  = COALESCE(:updtUserNam, t.UPDT_USER_NAM),
                RPLCTN_UPDT_TS = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT (
                ID, TRAN_ID, ADDR_TYPE,
                ST_LINE1, ST_LINE2, CITY, ST, CNTRY_NAM, POST_CD,
                ADDR_STAT, POST_CD_STAT,
                CRTE_TS, CRTE_USER_NAM, UPDT_USER_NAM, RPLCTN_UPDT_TS
            ) VALUES (
                :id, :tranId, :addrType,
                :stLine1, :stLine2, :city, :st, :cntryNam, :postCd,
                :addrStat, :postCdStat,
                SYSTIMESTAMP, :crteUserNam, :updtUserNam, SYSTIMESTAMP
            )
            """;

    private static final String SELECT_BY_TRAN_ID = """
            SELECT ID, TRAN_ID, ADDR_TYPE,
                   ST_LINE1, ST_LINE2, CITY, ST, CNTRY_NAM, POST_CD,
                   ADDR_STAT, POST_CD_STAT,
                   CRTE_TS, UPDT_TS, CRTE_USER_NAM, UPDT_USER_NAM, RPLCTN_UPDT_TS
            FROM SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL
            WHERE TRAN_ID = :tranId
            ORDER BY CRTE_TS
            """;

    private static final String DELETE_BY_TRAN_ID =
            "DELETE FROM SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL WHERE TRAN_ID = :tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendTranAddrDtlRowMapper rowMapper;

    @Override
    public void mergeAll(List<SendTranAddrDtl> addresses) {
        if (addresses == null || addresses.isEmpty()) return;
        log.debug("MERGE SEND_TRAN_ADDR_DTL batch size={} for tranId={}", addresses.size(), addresses.get(0).getTranId());
        SqlParameterSource[] batch = addresses.stream()
                .map(this::toParams)
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(MERGE, batch);
        log.debug("MERGE SEND_TRAN_ADDR_DTL complete");
    }

    @Override
    public List<SendTranAddrDtl> findByTranId(String tranId) {
        log.debug("SELECT SEND_TRAN_ADDR_DTL tranId={}", tranId);
        return jdbc.query(SELECT_BY_TRAN_ID,
                new MapSqlParameterSource("tranId", tranId), rowMapper);
    }

    @Override
    public void deleteByTranId(String tranId) {
        log.debug("DELETE SEND_TRAN_ADDR_DTL tranId={}", tranId);
        int rows = jdbc.update(DELETE_BY_TRAN_ID, new MapSqlParameterSource("tranId", tranId));
        log.debug("DELETE SEND_TRAN_ADDR_DTL removed {} rows for tranId={}", rows, tranId);
    }

    @Override
    public void deleteByTranIdNotIn(String tranId, List<String> keepIds) {
        log.debug("DELETE SEND_TRAN_ADDR_DTL obsolete addresses tranId={} keepCount={}", tranId, keepIds.size());
        String sql = "DELETE FROM SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL WHERE TRAN_ID = :tranId AND ID NOT IN (:ids)";
        int rows = jdbc.update(sql, new MapSqlParameterSource("tranId", tranId).addValue("ids", keepIds));
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
