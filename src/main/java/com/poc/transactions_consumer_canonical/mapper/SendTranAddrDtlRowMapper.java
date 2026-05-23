package com.poc.transactions_consumer_canonical.mapper;

import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class SendTranAddrDtlRowMapper implements RowMapper<SendTranAddrDtl> {

    @Override
    public SendTranAddrDtl mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SendTranAddrDtl.builder()
                .id(rs.getString("ID"))
                .tranId(rs.getString("TRAN_ID"))
                .addrType(rs.getString("ADDR_TYPE"))
                .stLine1(rs.getString("ST_LINE1"))
                .stLine2(rs.getString("ST_LINE2"))
                .city(rs.getString("CITY"))
                .st(rs.getString("ST"))
                .cntryNam(rs.getString("CNTRY_NAM"))
                .postCd(rs.getString("POST_CD"))
                .addrStat(rs.getString("ADDR_STAT"))
                .postCdStat(rs.getString("POST_CD_STAT"))
                .crteTs(toLocalDateTime(rs, "CRTE_TS"))
                .updtTs(toLocalDateTime(rs, "UPDT_TS"))
                .crteUserNam(rs.getString("CRTE_USER_NAM"))
                .updtUserNam(rs.getString("UPDT_USER_NAM"))
                .rplctnUpdtTs(toLocalDateTime(rs, "RPLCTN_UPDT_TS"))
                .build();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
