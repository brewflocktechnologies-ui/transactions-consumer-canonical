package com.poc.transactions_consumer_canonical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTranDtlResponse {

    private String tranId;
    private String paymtRef;
    private String unqTranRef;
    private String acqCntryNam;
    private Long acqIca;
    private String fundSrc;
    private String ichgRateDsgn;
    private String merchCatCd;
    private String paymtType;
    private String pointServIntrctn;
    private String tranPrps;
    /** TRAN_SETL_AMT is VARCHAR2(50) in the schema. */
    private String tranSetlAmt;
    private String bncGtwyRqst;
    private String bncGtwyResp;
    private String origRqstPyld;
    private String origRespPyld;
    private String tranfrAcptId;
    private String tranfrAcptNam;
    private String mcAssgnMerch;
    private String paymtFacltrId;
    private String subMerchId;
    private String tranfrTrmlId;
    private String tranfrAcptStLine1;
    private String tranfrAcptStLine2;
    private String tranfrAcptCity;
    private String tranfrAcptSt;
    private String tranfrAcptCntryNam;
    private String tranfrAcptPostCd;
    private LocalDateTime crteTs;
    private String crteUserNam;
    private LocalDateTime updtTs;
    private String updtUserNam;
    private String eventId;
    private LocalDateTime eventTs;
    private String eventCorltnId;
    private String msgVersion;
    private LocalDateTime tranCrteDt;
    private LocalDateTime rplctnUpdtTs;
    private String tranTypeIndCd;
    private String regulatedRateTypeCd;
    private String cvcRespDesc;
    private String procId;
    private String acqIdenCd;
    private String tranfrAcptMpgId;
    private String tranfrAcptMerchValue;
}
