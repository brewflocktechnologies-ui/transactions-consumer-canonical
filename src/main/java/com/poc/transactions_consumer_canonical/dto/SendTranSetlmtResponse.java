package com.poc.transactions_consumer_canonical.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Settlement-leg view of a SEND_TRAN_CLRG_SETLMT row — populated by SETTLEMENT events.
 * Surfaced on {@link SendTransactionResponse#getSettlement()} when a row exists for
 * the requested tranId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendTranSetlmtResponse {

    private String tranId;
    private LocalDate setlDt;
    private BigDecimal setlAmt;
    private String setlCurrCd;
    private String setlServId;
    private String setlIca;
    private String tranFileId;
}
