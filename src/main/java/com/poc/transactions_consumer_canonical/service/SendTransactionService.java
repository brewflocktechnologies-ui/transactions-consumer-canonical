package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;

public interface SendTransactionService {

    /**
     * Upserts the parent SEND_TRANSACTIONS row and any provided child rows
     * within a single transaction. Invoked exclusively by the Kafka canonical
     * consumer — there is no REST endpoint that calls this method.
     *
     * Null fields in a request are null-guarded: an existing non-null DB value
     * is preserved when the incoming value is null.
     * <ul>
     *   <li>tranDtl  — present → MERGE into SEND_TRAN_DTL (1:1); null → untouched</li>
     *   <li>recipDtl — present → MERGE into SEND_RECIP_DTL (1:1); null → untouched</li>
     *   <li>addrDtl  — null → untouched; [] → delete all; [...] → MERGE each by ID,
     *                  then prune IDs not present in the incoming list</li>
     * </ul>
     */
    SendTransactionResponse upsert(String tranId, SendTransactionRequest request);

    /**
     * Returns the full graph: parent + all child rows. Backs the read-only
     * REST endpoint {@code GET /api/v1/send-transactions/{tranId}}.
     */
    SendTransactionResponse findById(String tranId);
}
