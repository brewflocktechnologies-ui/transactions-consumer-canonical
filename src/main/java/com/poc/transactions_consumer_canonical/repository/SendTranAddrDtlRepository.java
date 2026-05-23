package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;

import java.util.List;

public interface SendTranAddrDtlRepository {

    /** Merge each address by ID — inserts new records, updates existing ones (null guard applied). */
    void mergeAll(List<SendTranAddrDtl> addresses);

    List<SendTranAddrDtl> findByTranId(String tranId);

    void deleteByTranId(String tranId);

    /** Remove all addresses for this tranId whose ID is NOT in keepIds. */
    void deleteByTranIdNotIn(String tranId, List<String> keepIds);
}
