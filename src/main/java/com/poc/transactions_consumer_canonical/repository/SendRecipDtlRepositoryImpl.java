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

    private static final String UPSERT = """
            MERGE INTO SEND_TXN_OWNER.SEND_RECIP_DTL t
            USING (SELECT :tranId AS TRAN_ID FROM DUAL) src
            ON (t.TRAN_ID = src.TRAN_ID)
            WHEN MATCHED THEN UPDATE SET
                SEND_FIRST_NAM          = COALESCE(:sendFirstNam,       t.SEND_FIRST_NAM),
                SEND_MID_NAM            = COALESCE(:sendMidNam,         t.SEND_MID_NAM),
                SEND_LST_NAM            = COALESCE(:sendLstNam,         t.SEND_LST_NAM),
                SEND_PHN                = COALESCE(:sendPhn,            t.SEND_PHN),
                SEND_EMAIL              = COALESCE(:sendEmail,          t.SEND_EMAIL),
                SEND_DOB                = COALESCE(:sendDob,            t.SEND_DOB),
                SEND_NATL               = COALESCE(:sendNatl,           t.SEND_NATL),
                SEND_BIRTH_CNTRY_NAM    = COALESCE(:sendBirthCntryNam,  t.SEND_BIRTH_CNTRY_NAM),
                SEND_ACCT_NUM           = COALESCE(:sendAcctNum,        t.SEND_ACCT_NUM),
                SEND_ACCT_URI           = COALESCE(:sendAcctUri,        t.SEND_ACCT_URI),
                SEND_GOVT_ID_URI        = CASE WHEN :sendGovtIdUri IS NOT NULL THEN TO_CLOB(:sendGovtIdUri) ELSE t.SEND_GOVT_ID_URI END,
                SEND_ACCT_NUM_TYPE      = COALESCE(:sendAcctNumType,    t.SEND_ACCT_NUM_TYPE),
                SEND_CARD_NUM           = COALESCE(:sendCardNum,        t.SEND_CARD_NUM),
                SEND_CARD_EXPIR_DT      = COALESCE(:sendCardExpirDt,    t.SEND_CARD_EXPIR_DT),
                SEND_ST_LINE1           = COALESCE(:sendStLine1,        t.SEND_ST_LINE1),
                SEND_ST_LINE2           = COALESCE(:sendStLine2,        t.SEND_ST_LINE2),
                SEND_CITY               = COALESCE(:sendCity,           t.SEND_CITY),
                SEND_ST                 = COALESCE(:sendSt,             t.SEND_ST),
                SEND_CNTRY_NAM          = COALESCE(:sendCntryNam,       t.SEND_CNTRY_NAM),
                SEND_POST_CD            = COALESCE(:sendPostCd,         t.SEND_POST_CD),
                RECIP_FIRST_NAM         = COALESCE(:recipFirstNam,      t.RECIP_FIRST_NAM),
                RECIP_MID_NAM           = COALESCE(:recipMidNam,        t.RECIP_MID_NAM),
                RECIP_LST_NAM           = COALESCE(:recipLstNam,        t.RECIP_LST_NAM),
                RECIP_PHN               = COALESCE(:recipPhn,           t.RECIP_PHN),
                RECIP_EMAIL             = COALESCE(:recipEmail,         t.RECIP_EMAIL),
                RECIP_DOB               = COALESCE(:recipDob,           t.RECIP_DOB),
                RECIP_NATL              = COALESCE(:recipNatl,          t.RECIP_NATL),
                RECIP_BIRTH_CNTRY_NAM   = COALESCE(:recipBirthCntryNam, t.RECIP_BIRTH_CNTRY_NAM),
                RECIP_ACCT_NUM          = COALESCE(:recipAcctNum,       t.RECIP_ACCT_NUM),
                RECIP_ACCT_URI          = COALESCE(:recipAcctUri,       t.RECIP_ACCT_URI),
                RECIP_GOVT_ID_URI       = CASE WHEN :recipGovtIdUri IS NOT NULL THEN TO_CLOB(:recipGovtIdUri) ELSE t.RECIP_GOVT_ID_URI END,
                RECIP_ACCT_NUM_TYPE     = COALESCE(:recipAcctNumType,   t.RECIP_ACCT_NUM_TYPE),
                RECIP_CARD_NUM          = COALESCE(:recipCardNum,       t.RECIP_CARD_NUM),
                RECIP_CARD_EXPIR_DT     = COALESCE(:recipCardExpirDt,   t.RECIP_CARD_EXPIR_DT),
                RECIP_ST_LINE1          = COALESCE(:recipStLine1,       t.RECIP_ST_LINE1),
                RECIP_ST_LINE2          = COALESCE(:recipStLine2,       t.RECIP_ST_LINE2),
                RECIP_CITY              = COALESCE(:recipCity,          t.RECIP_CITY),
                RECIP_ST                = COALESCE(:recipSt,            t.RECIP_ST),
                RECIP_CNTRY_NAM         = COALESCE(:recipCntryNam,      t.RECIP_CNTRY_NAM),
                RECIP_POST_CD           = COALESCE(:recipPostCd,        t.RECIP_POST_CD),
                UPDT_TS                 = SYSTIMESTAMP,
                UPDT_USER_NAM           = COALESCE(:updtUserNam,        t.UPDT_USER_NAM),
                TRAN_CRTE_DT            = COALESCE(:tranCrteDt,         t.TRAN_CRTE_DT),
                RPLCTN_UPDT_TS          = SYSTIMESTAMP
            WHEN NOT MATCHED THEN INSERT (
                TRAN_ID,
                SEND_FIRST_NAM, SEND_MID_NAM, SEND_LST_NAM, SEND_PHN, SEND_EMAIL,
                SEND_DOB, SEND_NATL, SEND_BIRTH_CNTRY_NAM, SEND_ACCT_NUM, SEND_ACCT_URI,
                SEND_GOVT_ID_URI, SEND_ACCT_NUM_TYPE, SEND_CARD_NUM, SEND_CARD_EXPIR_DT,
                SEND_ST_LINE1, SEND_ST_LINE2, SEND_CITY, SEND_ST, SEND_CNTRY_NAM, SEND_POST_CD,
                RECIP_FIRST_NAM, RECIP_MID_NAM, RECIP_LST_NAM, RECIP_PHN, RECIP_EMAIL,
                RECIP_DOB, RECIP_NATL, RECIP_BIRTH_CNTRY_NAM, RECIP_ACCT_NUM, RECIP_ACCT_URI,
                RECIP_GOVT_ID_URI, RECIP_ACCT_NUM_TYPE, RECIP_CARD_NUM, RECIP_CARD_EXPIR_DT,
                RECIP_ST_LINE1, RECIP_ST_LINE2, RECIP_CITY, RECIP_ST, RECIP_CNTRY_NAM, RECIP_POST_CD,
                CRTE_TS, CRTE_USER_NAM, UPDT_USER_NAM, TRAN_CRTE_DT, RPLCTN_UPDT_TS
            ) VALUES (
                :tranId,
                :sendFirstNam, :sendMidNam, :sendLstNam, :sendPhn, :sendEmail,
                :sendDob, :sendNatl, :sendBirthCntryNam, :sendAcctNum, :sendAcctUri,
                :sendGovtIdUri, :sendAcctNumType, :sendCardNum, :sendCardExpirDt,
                :sendStLine1, :sendStLine2, :sendCity, :sendSt, :sendCntryNam, :sendPostCd,
                :recipFirstNam, :recipMidNam, :recipLstNam, :recipPhn, :recipEmail,
                :recipDob, :recipNatl, :recipBirthCntryNam, :recipAcctNum, :recipAcctUri,
                :recipGovtIdUri, :recipAcctNumType, :recipCardNum, :recipCardExpirDt,
                :recipStLine1, :recipStLine2, :recipCity, :recipSt, :recipCntryNam, :recipPostCd,
                SYSTIMESTAMP, :crteUserNam, :updtUserNam, :tranCrteDt, SYSTIMESTAMP
            )
            """;

    private static final String SELECT_BY_TRAN_ID = """
            SELECT TRAN_ID,
                   SEND_FIRST_NAM, SEND_MID_NAM, SEND_LST_NAM, SEND_PHN, SEND_EMAIL,
                   SEND_DOB, SEND_NATL, SEND_BIRTH_CNTRY_NAM, SEND_ACCT_NUM, SEND_ACCT_URI,
                   SEND_GOVT_ID_URI, SEND_ACCT_NUM_TYPE, SEND_CARD_NUM, SEND_CARD_EXPIR_DT,
                   SEND_ST_LINE1, SEND_ST_LINE2, SEND_CITY, SEND_ST, SEND_CNTRY_NAM, SEND_POST_CD,
                   RECIP_FIRST_NAM, RECIP_MID_NAM, RECIP_LST_NAM, RECIP_PHN, RECIP_EMAIL,
                   RECIP_DOB, RECIP_NATL, RECIP_BIRTH_CNTRY_NAM, RECIP_ACCT_NUM, RECIP_ACCT_URI,
                   RECIP_GOVT_ID_URI, RECIP_ACCT_NUM_TYPE, RECIP_CARD_NUM, RECIP_CARD_EXPIR_DT,
                   RECIP_ST_LINE1, RECIP_ST_LINE2, RECIP_CITY, RECIP_ST, RECIP_CNTRY_NAM, RECIP_POST_CD,
                   CRTE_TS, CRTE_USER_NAM, UPDT_TS, UPDT_USER_NAM, TRAN_CRTE_DT, RPLCTN_UPDT_TS
            FROM SEND_TXN_OWNER.SEND_RECIP_DTL
            WHERE TRAN_ID = :tranId
            """;

    private static final String DELETE = "DELETE FROM SEND_TXN_OWNER.SEND_RECIP_DTL WHERE TRAN_ID = :tranId";

    private final NamedParameterJdbcTemplate jdbc;
    private final SendRecipDtlRowMapper rowMapper;

    @Override
    public void upsert(SendRecipDtl r) {
        log.debug("MERGE SEND_RECIP_DTL tranId={}", r.getTranId());
        jdbc.update(UPSERT, toParams(r));
        log.debug("MERGE SEND_RECIP_DTL complete tranId={}", r.getTranId());
    }

    @Override
    public Optional<SendRecipDtl> findByTranId(String tranId) {
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

    private MapSqlParameterSource toParams(SendRecipDtl r) {
        return new MapSqlParameterSource()
                .addValue("tranId",              r.getTranId(),              Types.VARCHAR)
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
