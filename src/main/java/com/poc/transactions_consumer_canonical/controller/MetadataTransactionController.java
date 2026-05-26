package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.service.MetadataTransactionService;
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

import java.util.List;
import java.util.Map;

/**
 * Read-only v2 surface. All write operations have been moved to the Kafka
 * canonical pipeline ({@code KafkaCanonicalConsumer}); only fetch-by-id and
 * metadata discovery are exposed over REST.
 */
@Slf4j
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Validated
@Tag(name = "send-transactions-v2",
        description = "Read-only metadata-driven endpoint — fetch by alias + id, and metadata discovery.")
public class MetadataTransactionController {

    private final MetadataTransactionService service;

    // ── Data endpoints ────────────────────────────────────────────────────────

    @Operation(summary = "Fetch by id — returns parent with all nested children")
    @GetMapping("/{alias}/{id}")
    public ResponseEntity<Map<String, Object>> findById(
            @PathVariable String alias,
            @PathVariable @Size(max = 50, message = "id must not exceed 50 characters") String id) {
        log.info("GET /api/v2/{}/{}", alias, id);
        return service.findByPk(alias, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(alias, id));
    }

    // ── Metadata discovery endpoints ──────────────────────────────────────────

    @Operation(summary = "List all registered table aliases with their column catalogs")
    @GetMapping("/_metadata")
    public ResponseEntity<List<Map<String, Object>>> listMetadata() {
        log.info("GET /api/v2/_metadata");
        return ResponseEntity.ok(service.listMetadata());
    }

    @Operation(summary = "Return the column catalog for a single registered table alias")
    @GetMapping("/_metadata/{alias}")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable String alias) {
        log.info("GET /api/v2/_metadata/{}", alias);
        return ResponseEntity.ok(service.getMetadata(alias));
    }
}
