package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.dto.PagedResponse;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.service.MetadataTransactionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * Single generic REST controller for any metadata-described table.
 * <p>
 * Resolution rules:
 * <ul>
 *   <li>{@code alias} can be either the URL alias (e.g. {@code send-transactions})
 *       or the DB table name (e.g. {@code SEND_TRANSACTIONS}).</li>
 *   <li>Mounted at {@code /api/v2/...} during migration so the old hand-written
 *       {@code SendTransactionController} at {@code /api/v1/...} keeps working
 *       byte-for-byte until cutover.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@Validated
public class MetadataTransactionController {

    private final MetadataTransactionService service;
    private final MetadataRegistry registry;

    /** Upsert any parent (with optional children) by alias and PK. */
    @PutMapping("/{alias}/{id}")
    public ResponseEntity<Map<String, Object>> upsert(
            @PathVariable String alias,
            @PathVariable @Size(max = 50, message = "id must not exceed 50 characters") String id,
            @RequestBody Map<String, Object> body) {
        log.info("PUT /api/v2/{}/{}", alias, id);
        return ResponseEntity.ok(service.upsert(alias, id, body));
    }

    /** Get a parent + full nested child graph. */
    @GetMapping("/{alias}/{id}")
    public ResponseEntity<Map<String, Object>> findById(
            @PathVariable String alias,
            @PathVariable @Size(max = 50, message = "id must not exceed 50 characters") String id) {
        log.info("GET /api/v2/{}/{}", alias, id);
        return service.findByPk(alias, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(alias, id));
    }

    /** Paginated parent-only list (no child data). */
    @GetMapping("/{alias}")
    public ResponseEntity<PagedResponse<Map<String, Object>>> findAll(
            @PathVariable String alias,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("GET /api/v2/{}?page={}&size={}", alias, page, size);
        return ResponseEntity.ok(service.findAll(alias, page, size));
    }

    /** Delete a parent and all its child rows. */
    @DeleteMapping("/{alias}/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String alias,
            @PathVariable @Size(max = 50, message = "id must not exceed 50 characters") String id) {
        log.info("DELETE /api/v2/{}/{}", alias, id);
        service.delete(alias, id);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────
    // Discovery — mitigates loss of Swagger/IDE auto-complete
    // ──────────────────────────────────────────────────────────

    /** Describe a single table (column metadata + child references). */
    @GetMapping("/_metadata/{alias}")
    public ResponseEntity<TableMetadata> describe(@PathVariable String alias) {
        return ResponseEntity.ok(registry.require(alias));
    }

    /** List every loaded table — column lists, children, etc. */
    @GetMapping("/_metadata")
    public ResponseEntity<Collection<TableMetadata>> describeAll() {
        return ResponseEntity.ok(registry.all());
    }
}
