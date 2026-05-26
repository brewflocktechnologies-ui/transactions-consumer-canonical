package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.dto.SendTranClrgSetlmtRequest;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Handles the dual-message 2nd-leg events that target SEND_TRAN_CLRG_SETLMT:
 * <ul>
 *   <li>CLEARING   — clearing leg (clrgSt, clrgDtTs, acquirer/rate/msg-reason data)</li>
 *   <li>SETTLEMENT — settlement leg (setlDt, setlAmt, setlCurrCd, setlServId, setlIca, tranFileId, …)</li>
 * </ul>
 *
 * <p>Both events upsert the same SEND_TRAN_CLRG_SETLMT row (1:1 with SEND_TRANSACTIONS,
 * keyed by tranId). The PAYMENT event must have populated the parent SEND_TRANSACTIONS
 * row first — if no parent row exists for the supplied tranId, the message is logged
 * and silently dropped.
 *
 * <p>The request is mapped via {@link com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine}
 * (driven by the {@code clrgSetlmt:} block in the event YAML), then serialized to a
 * {@code Map<String,Object>} via Jackson so the metadata-driven repository can bind
 * each column by its YAML jsonName.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearingEventService {

    /** Alias from {@code metadata/send_tran_clrg_setlmt.yaml}. */
    public static final String CLEARING_ALIAS = "send-tran-clrg-setlmt";
    /** Alias from {@code metadata/send_transactions.yaml} — used for the parent-existence guard. */
    public static final String PARENT_ALIAS = "send-transactions";

    private final GenericTableRepository repo;
    private final ObjectMapper objectMapper;

    /** CLEARING-event entry point. */
    @Transactional
    public void upsertClearing(SendTranClrgSetlmtRequest req) {
        upsertClrgSetlmt(req, "CLEARING");
    }

    /** SETTLEMENT-event entry point. */
    @Transactional
    public void upsertSettlement(SendTranClrgSetlmtRequest req) {
        upsertClrgSetlmt(req, "SETTLEMENT");
    }

    /**
     * Common path for both CLEARING and SETTLEMENT events.
     * <ol>
     *   <li>Validate {@code tranId} is present on the request.</li>
     *   <li>Confirm a parent SEND_TRANSACTIONS row exists; if not, log and skip.</li>
     *   <li>MERGE the request (as a Map keyed by YAML jsonName) into SEND_TRAN_CLRG_SETLMT.</li>
     * </ol>
     */
    private void upsertClrgSetlmt(SendTranClrgSetlmtRequest req, String eventLabel) {
        if (req == null || req.getTranId() == null || req.getTranId().isBlank()) {
            throw new IllegalArgumentException(eventLabel + " request missing required 'tranId'");
        }
        String tranId = req.getTranId();
        if (repo.findByPk(PARENT_ALIAS, tranId).isEmpty()) {
            log.warn("[{}] No parent SEND_TRANSACTIONS row for tranId={} — message ignored "
                    + "(expected the PAYMENT event to have arrived first).", eventLabel, tranId);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(req, Map.class);
        log.info("[{}] Upserting SEND_TRAN_CLRG_SETLMT for tranId={}", eventLabel, tranId);
        repo.upsert(CLEARING_ALIAS, payload);
        log.info("[{}] Upsert complete for tranId={}", eventLabel, tranId);
    }
}
