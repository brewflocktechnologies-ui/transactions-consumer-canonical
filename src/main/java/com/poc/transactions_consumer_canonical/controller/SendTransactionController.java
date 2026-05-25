package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only v1 surface. All write operations have been moved to the Kafka
 * canonical pipeline ({@code KafkaCanonicalConsumer}); only fetch-by-id is
 * exposed over REST.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/send-transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "send-transactions-v1",
        description = "Read-only typed endpoint — fetch the full nested graph by tranId.")
public class SendTransactionController {

    private final SendTransactionService service;

    /**
     * Retrieve a transaction with the full nested graph
     * (tranDtl, recipDtl, addrDtl[]).
     */
    @Operation(summary = "Fetch a transaction by id with the full nested graph (parent + children)")
    @GetMapping("/{tranId}")
    public ResponseEntity<SendTransactionResponse> findById(
            @PathVariable @Size(max = 50, message = "tranId must not exceed 50 characters") String tranId) {
        log.info("GET /api/v1/send-transactions/{}", tranId);
        return ResponseEntity.ok(service.findById(tranId));
    }
}
