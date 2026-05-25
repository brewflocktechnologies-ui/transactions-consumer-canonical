package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.model.SendTranDtl;

import java.util.Optional;

public interface SendTranDtlRepository {

    void upsert(SendTranDtl tranDtl);

    Optional<SendTranDtl> findByTranId(String tranId);
}
