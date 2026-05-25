package com.poc.transactions_consumer_canonical.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTransaction {

    private String tranId;
    private String tranInitId;
    private String origInstId;
    private String origInstNam;
    private String tranfrAcptNam;
    private String tranfrAcptId;
    private LocalDateTime tranCrteDt;
    private String tranType;
    private String custRefNum;
    private String curStat;
    private String origStat;
    private String useCase;
    private String msgType;
    private String refId;
    private String swSerNum;
    private String bnkntRefNum;
    private String sendAcct;
    private String recipAcct;
    private BigDecimal tranAmt;
    private String tranCurr;
    private String errCd;
    private String fundAvail;
    private String corltnId;
    private LocalDateTime crteTs;
    private String crteUserNam;
    private LocalDateTime updtTs;
    private String updtUserNam;
    private LocalDateTime rplctnUpdtTs;
    private String ntwrkCd;
    private String ntwrkRespCd;
    private String tranInitNam;
    private String namStat;
    private String cvcStat;
    private String cvcRespCd;
    private String acctNum;
    private String acctType;
    private String acctHoldNam;
    private String errCdDesc;
    private Boolean nonFinTxn;
    private Boolean recipElig;
    private String ntwrkRespCdDesc;
}
