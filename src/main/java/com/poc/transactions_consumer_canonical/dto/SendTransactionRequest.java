package com.poc.transactions_consumer_canonical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTransactionRequest {

    // ── Core ─────────────────────────────────────────────────
    @Size(max = 50)
    private String tranInitId;

    @Size(max = 50)
    private String origInstId;

    @Size(max = 100)
    private String origInstNam;

    @Size(max = 100)
    private String tranfrAcptNam;

    @Size(max = 50)
    private String tranfrAcptId;

    @NotNull(message = "tranCrteDt is required")
    private LocalDateTime tranCrteDt;

    @NotBlank(message = "tranType is required")
    @Size(max = 50)
    private String tranType;

    @Size(max = 50)
    private String custRefNum;

    @Size(max = 50)
    private String curStat;

    @Size(max = 50)
    private String origStat;

    @Size(max = 100)
    private String useCase;

    @Size(max = 50)
    private String msgType;

    @Size(max = 100)
    private String refId;

    @Size(max = 50)
    private String swSerNum;

    @Size(max = 100)
    private String bnkntRefNum;

    @Size(max = 100)
    private String sendAcct;

    @Size(max = 100)
    private String recipAcct;

    private BigDecimal tranAmt;

    @Size(max = 10)
    private String tranCurr;

    @Size(max = 50)
    private String errCd;

    @Size(max = 50)
    private String fundAvail;

    @Size(max = 100)
    private String corltnId;

    // ── Audit ────────────────────────────────────────────────
    @Size(max = 100)
    private String crteUserNam;

    @Size(max = 100)
    private String updtUserNam;

    // ── Network / Validation ─────────────────────────────────
    @Size(max = 50)
    private String ntwrkCd;

    @Size(max = 50)
    private String ntwrkRespCd;

    @Size(max = 100)
    private String tranInitNam;

    @Size(max = 50)
    private String namStat;

    @Size(max = 50)
    private String cvcStat;

    @Size(max = 50)
    private String cvcRespCd;

    @Size(max = 100)
    private String acctNum;

    @Size(max = 50)
    private String acctType;

    @Size(max = 100)
    private String acctHoldNam;

    @Size(max = 500)
    private String errCdDesc;

    /**
     * null  → leave the existing NON_FIN_TXN value untouched (COALESCE guard applied in MERGE).
     * true  → set NON_FIN_TXN = 1.
     * false → set NON_FIN_TXN = 0.
     */
    private Boolean nonFinTxn;

    /**
     * Recipient (account) eligibility flag.
     * null  → leave the existing RECIP_ELIG value untouched (COALESCE guard applied in MERGE).
     * true  → set RECIP_ELIG = 1.
     * false → set RECIP_ELIG = 0.
     */
    private Boolean recipElig;

    @Size(max = 500)
    private String ntwrkRespCdDesc;

    // ── Child tables (null = do not touch; present = upsert/replace) ──
    @Valid
    private SendTranDtlRequest tranDtl;

    @Valid
    private SendRecipDtlRequest recipDtl;

    /**
     * When non-null, merges each address by ID (null guard applied per field).
     * Pass an empty list to delete all existing addresses.
     */
    private List<@Valid SendTranAddrDtlRequest> addrDtl;
}
