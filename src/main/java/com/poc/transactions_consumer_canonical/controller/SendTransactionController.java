package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.dto.PagedResponse;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/send-transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "send-transactions-v1",
        description = "Typed POJO endpoints — stable contract with full request/response schemas.")
public class SendTransactionController {

    private final SendTransactionService service;

    /**
     * Upsert a transaction and its children.
     * Child sections are optional:
     *   - omit tranDtl / recipDtl  → existing rows are untouched
     *   - include tranDtl / recipDtl → MERGE (insert or update)
     *   - include addrDtl (even []) → merges by ID; empty list deletes all addresses
     */
    @Operation(summary = "Upsert a transaction and its child rows (MERGE with COALESCE null-guard)")
    @PutMapping("/{tranId}")
    public ResponseEntity<SendTransactionResponse> upsert(
            @PathVariable @Size(max = 50, message = "tranId must not exceed 50 characters") String tranId,
            @Valid @RequestBody SendTransactionRequest request) {
        log.info("PUT /api/v1/send-transactions/{}", tranId);
        return ResponseEntity.ok(service.upsert(tranId, request));
    }

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

    /**
     * Paginated list of parent rows only (no child data).
     */
    @Operation(summary = "Paginated list of parent rows (no child data)")
    @GetMapping
    public ResponseEntity<PagedResponse<SendTransactionResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("GET /api/v1/send-transactions?page={}&size={}", page, size);
        return ResponseEntity.ok(service.findAll(page, size));
    }

    /**
     * Delete a transaction and all its child rows.
     */
    @Operation(summary = "Delete the parent and all its child rows (no DB cascade — service ordering)")
    @DeleteMapping("/{tranId}")
    public ResponseEntity<Void> delete(
            @PathVariable @Size(max = 50, message = "tranId must not exceed 50 characters") String tranId) {
        log.info("DELETE /api/v1/send-transactions/{}", tranId);
        service.delete(tranId);
        return ResponseEntity.noContent().build();
    }
}
