package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.model.SendRecipDtl;

import java.util.Optional;

public interface SendRecipDtlRepository {

    void upsert(SendRecipDtl recipDtl);

    Optional<SendRecipDtl> findByTranId(String tranId);
}
