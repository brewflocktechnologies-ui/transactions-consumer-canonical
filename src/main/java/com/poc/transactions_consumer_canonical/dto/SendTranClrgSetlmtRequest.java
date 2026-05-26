package com.poc.transactions_consumer_canonical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Canonical request shape for the SEND_TRAN_CLRG_SETLMT table — populated by
 * {@code CanonicalMappingEngine.applyTo(...)} for both CLEARING and SETTLEMENT
 * events.
 *
 * <p>Field names mirror the {@code jsonName} entries in
 * {@code metadata/send_tran_clrg_setlmt.yaml}.
 *
 * <h3>Adding a new SEND_TRAN_CLRG_SETLMT column</h3>
 * <ol>
 *   <li>Add the column row in {@code metadata/send_tran_clrg_setlmt.yaml}.</li>
 *   <li>Add the matching field here (same camelCase name + correct Java type).</li>
 *   <li>Add a mapping line in {@code canonical-mappings/CLEARING.yaml}
 *       and/or {@code canonical-mappings/SETTLEMENT.yaml} —
 *       {@code { source: <fieldOnTransactionEventAxonMessage>, target: <fieldNameHere> }}.</li>
 *   <li>If the source side doesn't already carry that data, add the source
 *       field to {@link com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage}.</li>
 * </ol>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTranClrgSetlmtRequest {

    @NotBlank
    @Size(max = 50)
    private String tranId;

    // ── Clearing status (DB-enforced NOT NULL) ──────────────────────────
    @NotBlank
    @Size(max = 50)
    private String clrgSt;

    @NotNull
    private LocalDateTime clrgDtTs;

    // ── Acquirer / rate / message reason ────────────────────────────────
    @Size(max = 250)
    private String acqIcaRefTxt;

    @Size(max = 60)
    private String rateTypeInd;

    @Size(max = 60)
    private String msgRsnCd;

    @Size(max = 60)
    private String msgRsnDesc;

    @Size(max = 60)
    private String ichgRateDsgnCd;

    @Size(max = 100)
    private String ichgFee;

    @Size(max = 100)
    private String busnSrvAgmt;

    // ── Settlement ──────────────────────────────────────────────────────
    private LocalDate setlDt;
    private BigDecimal setlAmt;

    @Size(max = 3)
    private String setlCurrCd;

    @Size(max = 11)
    private String setlServId;

    @Size(max = 11)
    private String setlIca;

    @Size(max = 25)
    private String tranFileId;

    // ── Audit (user-supplied; timestamps are SYSTIMESTAMP) ──────────────
    @Size(max = 20)
    private String crteUserNam;

    @Size(max = 20)
    private String updtUserNam;
}
