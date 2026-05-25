package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.mapper.SendRecipDtlRowMapper;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
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
public class SendRecipDtlRepositoryImpl implements SendRecipDtlRepository {

    private static final String TRAN_ID_PARAM = "tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendRecipDtlRowMapper rowMapper;
    private final SqlQueries sqlQueries;

    @Override
    public void upsert(SendRecipDtl r) {
        log.debug("MERGE SEND_RECIP_DTL tranId={}", r.getTranId());
        jdbc.update(sqlQueries.sendRecipDtlUpsert(), toParams(r));
        log.debug("MERGE SEND_RECIP_DTL complete tranId={}", r.getTranId());
    }

    @Override
    public Optional<SendRecipDtl> findByTranId(String tranId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sqlQueries.sendRecipDtlSelectByTranId(),
                            new MapSqlParameterSource(TRAN_ID_PARAM, tranId), rowMapper));
        } catch (EmptyResultDataAccessException _) {
            return Optional.empty();
        }
    }

    private MapSqlParameterSource toParams(SendRecipDtl r) {
        return new MapSqlParameterSource()
                .addValue(TRAN_ID_PARAM,         r.getTranId(),              Types.VARCHAR)
                .addValue("sendFirstNam",         r.getSendFirstNam(),        Types.VARCHAR)
                .addValue("sendMidNam",           r.getSendMidNam(),          Types.VARCHAR)
                .addValue("sendLstNam",           r.getSendLstNam(),          Types.VARCHAR)
                .addValue("sendPhn",              r.getSendPhn(),             Types.VARCHAR)
                .addValue("sendEmail",            r.getSendEmail(),           Types.VARCHAR)
                .addValue("sendDob",              r.getSendDob(),             Types.DATE)
                .addValue("sendNatl",             r.getSendNatl(),            Types.VARCHAR)
                .addValue("sendBirthCntryNam",    r.getSendBirthCntryNam(),   Types.VARCHAR)
                .addValue("sendAcctNum",          r.getSendAcctNum(),         Types.VARCHAR)
                .addValue("sendAcctUri",          r.getSendAcctUri(),         Types.VARCHAR)
                .addValue("sendGovtIdUri",        r.getSendGovtIdUri(),       Types.VARCHAR)
                .addValue("sendAcctNumType",      r.getSendAcctNumType(),     Types.VARCHAR)
                .addValue("sendCardNum",          r.getSendCardNum(),         Types.VARCHAR)
                .addValue("sendCardExpirDt",      r.getSendCardExpirDt(),     Types.VARCHAR)
                .addValue("sendStLine1",          r.getSendStLine1(),         Types.VARCHAR)
                .addValue("sendStLine2",          r.getSendStLine2(),         Types.VARCHAR)
                .addValue("sendCity",             r.getSendCity(),            Types.VARCHAR)
                .addValue("sendSt",               r.getSendSt(),              Types.VARCHAR)
                .addValue("sendCntryNam",         r.getSendCntryNam(),        Types.VARCHAR)
                .addValue("sendPostCd",           r.getSendPostCd(),          Types.VARCHAR)
                .addValue("recipFirstNam",        r.getRecipFirstNam(),       Types.VARCHAR)
                .addValue("recipMidNam",          r.getRecipMidNam(),         Types.VARCHAR)
                .addValue("recipLstNam",          r.getRecipLstNam(),         Types.VARCHAR)
                .addValue("recipPhn",             r.getRecipPhn(),            Types.VARCHAR)
                .addValue("recipEmail",           r.getRecipEmail(),          Types.VARCHAR)
                .addValue("recipDob",             r.getRecipDob(),            Types.DATE)
                .addValue("recipNatl",            r.getRecipNatl(),           Types.VARCHAR)
                .addValue("recipBirthCntryNam",   r.getRecipBirthCntryNam(),  Types.VARCHAR)
                .addValue("recipAcctNum",         r.getRecipAcctNum(),        Types.VARCHAR)
                .addValue("recipAcctUri",         r.getRecipAcctUri(),        Types.VARCHAR)
                .addValue("recipGovtIdUri",       r.getRecipGovtIdUri(),      Types.VARCHAR)
                .addValue("recipAcctNumType",     r.getRecipAcctNumType(),    Types.VARCHAR)
                .addValue("recipCardNum",         r.getRecipCardNum(),        Types.VARCHAR)
                .addValue("recipCardExpirDt",     r.getRecipCardExpirDt(),    Types.VARCHAR)
                .addValue("recipStLine1",         r.getRecipStLine1(),        Types.VARCHAR)
                .addValue("recipStLine2",         r.getRecipStLine2(),        Types.VARCHAR)
                .addValue("recipCity",            r.getRecipCity(),           Types.VARCHAR)
                .addValue("recipSt",              r.getRecipSt(),             Types.VARCHAR)
                .addValue("recipCntryNam",        r.getRecipCntryNam(),       Types.VARCHAR)
                .addValue("recipPostCd",          r.getRecipPostCd(),         Types.VARCHAR)
                .addValue("crteUserNam",          r.getCrteUserNam(),         Types.VARCHAR)
                .addValue("updtUserNam",          r.getUpdtUserNam(),         Types.VARCHAR)
                .addValue("tranCrteDt",           r.getTranCrteDt(),          Types.TIMESTAMP);
    }
}
