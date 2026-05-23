package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.dto.PagedResponse;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;

public interface SendTransactionService {

    /**
     * Upserts the parent SEND_TRANSACTIONS row and any provided child rows
     * within a single transaction. Null fields in a request are null-guarded:
     * an existing non-null DB value is preserved when the incoming value is null.
     * <ul>
     *   <li>tranDtl  — present → MERGE into SEND_TRAN_DTL (1:1); null → untouched</li>
     *   <li>recipDtl — present → MERGE into SEND_RECIP_DTL (1:1); null → untouched</li>
     *   <li>addrDtl  — null → untouched; [] → delete all; [...] → MERGE each by ID,
     *                  then prune IDs not present in the incoming list</li>
     * </ul>
     */
    SendTransactionResponse upsert(String tranId, SendTransactionRequest request);

    /**
     * Returns the full graph: parent + all child rows.
     */
    SendTransactionResponse findById(String tranId);

    /**
     * Returns a paginated list of parent rows only (no child data).
     */
    PagedResponse<SendTransactionResponse> findAll(int page, int size);

    /**
     * Deletes children first (no DB cascade), then deletes the parent.
     */
    void delete(String tranId);
}
