package com.poc.transactions_consumer_canonical.mapper;

import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class SendRecipDtlRowMapper implements RowMapper<SendRecipDtl> {

    @Override
    public SendRecipDtl mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SendRecipDtl.builder()
                .tranId(rs.getString("TRAN_ID"))
                .sendFirstNam(rs.getString("SEND_FIRST_NAM"))
                .sendMidNam(rs.getString("SEND_MID_NAM"))
                .sendLstNam(rs.getString("SEND_LST_NAM"))
                .sendPhn(rs.getString("SEND_PHN"))
                .sendEmail(rs.getString("SEND_EMAIL"))
                .sendDob(toLocalDate(rs, "SEND_DOB"))
                .sendNatl(rs.getString("SEND_NATL"))
                .sendBirthCntryNam(rs.getString("SEND_BIRTH_CNTRY_NAM"))
                .sendAcctNum(rs.getString("SEND_ACCT_NUM"))
                .sendAcctUri(rs.getString("SEND_ACCT_URI"))
                .sendGovtIdUri(rs.getString("SEND_GOVT_ID_URI"))
                .sendAcctNumType(rs.getString("SEND_ACCT_NUM_TYPE"))
                .sendCardNum(rs.getString("SEND_CARD_NUM"))
                .sendCardExpirDt(rs.getString("SEND_CARD_EXPIR_DT"))
                .sendStLine1(rs.getString("SEND_ST_LINE1"))
                .sendStLine2(rs.getString("SEND_ST_LINE2"))
                .sendCity(rs.getString("SEND_CITY"))
                .sendSt(rs.getString("SEND_ST"))
                .sendCntryNam(rs.getString("SEND_CNTRY_NAM"))
                .sendPostCd(rs.getString("SEND_POST_CD"))
                .recipFirstNam(rs.getString("RECIP_FIRST_NAM"))
                .recipMidNam(rs.getString("RECIP_MID_NAM"))
                .recipLstNam(rs.getString("RECIP_LST_NAM"))
                .recipPhn(rs.getString("RECIP_PHN"))
                .recipEmail(rs.getString("RECIP_EMAIL"))
                .recipDob(toLocalDate(rs, "RECIP_DOB"))
                .recipNatl(rs.getString("RECIP_NATL"))
                .recipBirthCntryNam(rs.getString("RECIP_BIRTH_CNTRY_NAM"))
                .recipAcctNum(rs.getString("RECIP_ACCT_NUM"))
                .recipAcctUri(rs.getString("RECIP_ACCT_URI"))
                .recipGovtIdUri(rs.getString("RECIP_GOVT_ID_URI"))
                .recipAcctNumType(rs.getString("RECIP_ACCT_NUM_TYPE"))
                .recipCardNum(rs.getString("RECIP_CARD_NUM"))
                .recipCardExpirDt(rs.getString("RECIP_CARD_EXPIR_DT"))
                .recipStLine1(rs.getString("RECIP_ST_LINE1"))
                .recipStLine2(rs.getString("RECIP_ST_LINE2"))
                .recipCity(rs.getString("RECIP_CITY"))
                .recipSt(rs.getString("RECIP_ST"))
                .recipCntryNam(rs.getString("RECIP_CNTRY_NAM"))
                .recipPostCd(rs.getString("RECIP_POST_CD"))
                .crteTs(toLocalDateTime(rs, "CRTE_TS"))
                .crteUserNam(rs.getString("CRTE_USER_NAM"))
                .updtTs(toLocalDateTime(rs, "UPDT_TS"))
                .updtUserNam(rs.getString("UPDT_USER_NAM"))
                .tranCrteDt(toLocalDateTime(rs, "TRAN_CRTE_DT"))
                .rplctnUpdtTs(toLocalDateTime(rs, "RPLCTN_UPDT_TS"))
                .build();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private LocalDate toLocalDate(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d != null ? d.toLocalDate() : null;
    }
}
