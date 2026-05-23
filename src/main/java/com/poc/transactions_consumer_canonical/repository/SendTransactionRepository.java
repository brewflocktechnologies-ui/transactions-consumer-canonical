package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.model.SendTransaction;

import java.util.List;
import java.util.Optional;

public interface SendTransactionRepository {

    void upsert(SendTransaction transaction);

    Optional<SendTransaction> findById(String tranId);

    List<SendTransaction> findAll(int offset, int size);

    long count();

    boolean deleteById(String tranId);
}
