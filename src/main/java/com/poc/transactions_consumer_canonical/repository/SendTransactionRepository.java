package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.model.SendTransaction;

import java.util.Optional;

public interface SendTransactionRepository {

    void upsert(SendTransaction transaction);

    Optional<SendTransaction> findById(String tranId);
}
