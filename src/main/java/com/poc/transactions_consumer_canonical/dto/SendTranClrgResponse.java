package com.poc.transactions_consumer_canonical.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Clearing-leg view of a SEND_TRAN_CLRG_SETLMT row — populated by CLEARING events.
 * Surfaced on {@link SendTransactionResponse#getClearing()} when a row exists for
 * the requested tranId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendTranClrgResponse {

    private String tranId;
    private String clrgSt;
    private LocalDateTime clrgDtTs;
    private String acqIcaRefTxt;
    private String rateTypeInd;
    private String msgRsnCd;
    private String msgRsnDesc;
    private String ichgRateDsgnCd;
    private String ichgFee;
    private String busnSrvAgmt;

    private LocalDateTime crteTs;
    private String crteUserNam;
    private LocalDateTime updtTs;
    private String updtUserNam;
    private LocalDateTime rplctnUpdtTs;
}
