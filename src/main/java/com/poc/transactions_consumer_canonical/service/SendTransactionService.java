package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;

import java.util.Map;

public interface SendTransactionService {

    /**
     * Upserts the parent SEND_TRANSACTIONS row and any provided child rows
     * within a single transaction. Invoked exclusively by the Kafka canonical
     * consumer — there is no REST endpoint that calls this method.
     *
     * <p>The {@code canonical} payload mirrors the {@link com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine}
     * output shape:
     * <ul>
     *   <li>Parent column values keyed by {@code jsonName} at the top level</li>
     *   <li>{@code tranDtl}  → 1:1 child map (optional; absent → untouched)</li>
     *   <li>{@code recipDtl} → 1:1 child map (optional; absent → untouched)</li>
     *   <li>{@code addrDtl}  → 1:many child list:
     *     <ul>
     *       <li>{@code null} or absent → addresses untouched</li>
     *       <li>{@code []} → delete all addresses for this {@code tranId}</li>
     *       <li>{@code [...]} → MERGE each row by id, then prune ids not in the list</li>
     *     </ul>
     *   </li>
     * </ul>
     * Missing field values land in the DB as {@code null} via the COALESCE
     * null-guard in the generated MERGE SQL — incoming null preserves any
     * existing non-null DB value.
     *
     * @return the persisted full graph (same shape as {@link #findById(String)})
     */
    SendTransactionResponse upsert(String tranId, Map<String, Object> canonical);

    /**
     * Returns the full graph: parent + all child rows. Backs the read-only
     * REST endpoint {@code GET /api/v1/send-transactions/{tranId}}.
     */
    SendTransactionResponse findById(String tranId);
}
