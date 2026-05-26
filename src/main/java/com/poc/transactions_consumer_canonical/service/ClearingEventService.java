package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
 * <p>Payloads are {@code Map<String,Object>} keyed by the {@code jsonName} from the
 * {@code metadata/send_tran_clrg_setlmt.yaml} descriptor (as produced by the
 * {@link com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine}
 * via the {@code clrgSetlmt:} block of the event YAML).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearingEventService {

    /** Alias from {@code metadata/send_tran_clrg_setlmt.yaml}. */
    public static final String CLEARING_ALIAS = "send-tran-clrg-setlmt";
    /** Alias from {@code metadata/send_transactions.yaml} — used for the parent-existence guard. */
    public static final String PARENT_ALIAS = "send-transactions";

    private static final String KEY_TRAN_ID = "tranId";

    private final GenericTableRepository repo;

    /** CLEARING-event entry point. */
    @Transactional
    public void upsertClearing(String tranId, Map<String, Object> payload) {
        upsertClrgSetlmt(tranId, payload, "CLEARING");
    }

    /** SETTLEMENT-event entry point. */
    @Transactional
    public void upsertSettlement(String tranId, Map<String, Object> payload) {
        upsertClrgSetlmt(tranId, payload, "SETTLEMENT");
    }

    /**
     * Common path for both CLEARING and SETTLEMENT events.
     * <ol>
     *   <li>Validate {@code tranId} is present.</li>
     *   <li>Confirm a parent SEND_TRANSACTIONS row exists; if not, log and skip.</li>
     *   <li>MERGE the payload (forced to contain the path-derived {@code tranId}) into SEND_TRAN_CLRG_SETLMT.</li>
     * </ol>
     */
    private void upsertClrgSetlmt(String tranId, Map<String, Object> payload, String eventLabel) {
        if (tranId == null || tranId.isBlank()) {
            throw new IllegalArgumentException(eventLabel + " request missing required 'tranId'");
        }
        if (repo.findByPk(PARENT_ALIAS, tranId).isEmpty()) {
            log.warn("[{}] No parent SEND_TRANSACTIONS row for tranId={} — message ignored "
                    + "(expected the PAYMENT event to have arrived first).", eventLabel, tranId);
            return;
        }
        Map<String, Object> withTranId = payload == null ? new HashMap<>() : new HashMap<>(payload);
        withTranId.put(KEY_TRAN_ID, tranId);
        log.info("[{}] Upserting SEND_TRAN_CLRG_SETLMT for tranId={}", eventLabel, tranId);
        repo.upsert(CLEARING_ALIAS, withTranId);
        log.info("[{}] Upsert complete for tranId={}", eventLabel, tranId);
    }
}
