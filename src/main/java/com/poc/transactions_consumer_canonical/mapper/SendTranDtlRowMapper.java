package com.poc.transactions_consumer_canonical.mapper;

import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class SendTranDtlRowMapper implements RowMapper<SendTranDtl> {

    @Override
    public SendTranDtl mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SendTranDtl.builder()
                .tranId(rs.getString("TRAN_ID"))
                .paymtRef(rs.getString("PAYMT_REF"))
                .unqTranRef(rs.getString("UNQ_TRAN_REF"))
                .acqCntryNam(rs.getString("ACQ_CNTRY_NAM"))
                .acqIca(rs.getObject("ACQ_ICA", Long.class))
                .fundSrc(rs.getString("FUND_SRC"))
                .ichgRateDsgn(rs.getString("ICHG_RATE_DSGN"))
                .merchCatCd(rs.getString("MERCH_CAT_CD"))
                .paymtType(rs.getString("PAYMT_TYPE"))
                .pointServIntrctn(rs.getString("POINT_SERV_INTRCTN"))
                .tranPrps(rs.getString("TRAN_PRPS"))
                .tranSetlAmt(rs.getBigDecimal("TRAN_SETL_AMT"))
                .bncGtwyRqst(rs.getString("BNC_GTWY_RQST"))
                .bncGtwyResp(rs.getString("BNC_GTWY_RESP"))
                .origRqstPyld(rs.getString("ORIG_RQST_PYLD"))
                .origRespPyld(rs.getString("ORIG_RESP_PYLD"))
                .tranfrAcptId(rs.getString("TRANFR_ACPT_ID"))
                .tranfrAcptNam(rs.getString("TRANFR_ACPT_NAM"))
                .mcAssgnMerch(rs.getString("MC_ASSGN_MERCH"))
                .paymtFacltrId(rs.getString("PAYMT_FACILTR_ID"))
                .subMerchId(rs.getString("SUB_MERCH_ID"))
                .tranfrTrmlId(rs.getString("TRANFR_TRML_ID"))
                .tranfrAcptStLine1(rs.getString("TRANFR_ACPT_ST_LINE1"))
                .tranfrAcptStLine2(rs.getString("TRANFR_ACPT_ST_LINE2"))
                .tranfrAcptCity(rs.getString("TRANFR_ACPT_CITY"))
                .tranfrAcptSt(rs.getString("TRANFR_ACPT_ST"))
                .tranfrAcptCntryNam(rs.getString("TRANFR_ACPT_CNTRY_NAM"))
                .tranfrAcptPostCd(rs.getString("TRANFR_ACPT_POST_CD"))
                .crteTs(toLocalDateTime(rs, "CRTE_TS"))
                .crteUserNam(rs.getString("CRTE_USER_NAM"))
                .updtTs(toLocalDateTime(rs, "UPDT_TS"))
                .updtUserNam(rs.getString("UPDT_USER_NAM"))
                .eventId(rs.getString("EVENT_ID"))
                .eventTs(toLocalDateTime(rs, "EVENT_TS"))
                .eventCorltnId(rs.getString("EVENT_CORLTN_ID"))
                .msgVersion(rs.getString("MSG_VERSION"))
                .tranCrteDt(toLocalDateTime(rs, "TRAN_CRTE_DT"))
                .rplctnUpdtTs(toLocalDateTime(rs, "RPLCTN_UPDT_TS"))
                .tranTypeIndCd(rs.getString("TRAN_TYPE_IND_CD"))
                .regulatedRateTypeCd(rs.getString("REGULATED_RATE_TYPE_CD"))
                .cvcRespDesc(rs.getString("CVC_RESP_DESC"))
                .procId(rs.getString("PROC_ID"))
                .acqIdenCd(rs.getString("ACQ_IDEN_CD"))
                .tranfrAcptMpgId(rs.getString("TRANFR_ACPT_MPG_ID"))
                .tranfrAcptMerchValue(rs.getString("TRANFR_ACPT_MERCH_VALUE"))
                .build();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
