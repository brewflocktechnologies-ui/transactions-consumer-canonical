package com.poc.transactions_consumer_canonical.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTranDtlRequest {

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
    private BigDecimal tranSetlAmt;
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
    private String crteUserNam;
    private String updtUserNam;
    private String eventId;
    private LocalDateTime eventTs;
    private String eventCorltnId;
    private String msgVersion;

    @NotNull(message = "tranCrteDt is required")
    private LocalDateTime tranCrteDt;

    private String tranTypeIndCd;
    private String regulatedRateTypeCd;
    private String cvcRespDesc;
    private String procId;
    private String acqIdenCd;
    private String tranfrAcptMpgId;
    private String tranfrAcptMerchValue;
}
