package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.dto.PagedResponse;
import com.poc.transactions_consumer_canonical.exception.MetadataValidationException;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.metadata.ChildMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import com.poc.transactions_consumer_canonical.validation.MetadataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single generic orchestrator for parent + child table operations.
 * Driven entirely by {@link TableMetadata} — no per-table code paths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataTransactionService {

    private final MetadataRegistry registry;
    private final GenericTableRepository repo;
    private final MetadataValidator validator;

    // ──────────────────────────────────────────────────────────
    // Upsert
    // ──────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> upsert(String parentAlias, Object pk, Map<String, Object> payload) {
        TableMetadata parent = registry.require(parentAlias);
        if (payload == null) payload = new LinkedHashMap<>();

        // Path-variable PK overrides body
        payload.put(parent.pkColumn().getJsonName(), pk);

        // Parent payload only (strip child sections)
        Map<String, Object> parentPayload = parentPayloadOnly(parent, payload);
        throwIfInvalid(parent, parentPayload);

        log.info("Upsert parent {} pk={}", parent.getName(), pk);
        repo.upsert(parent.getName(), parentPayload);

        // Children
        for (ChildMetadata cm : parent.getChildren()) {
            Object section = payload.get(cm.getJsonName());
            if (section == null) {
                log.debug("Child {} not present in payload — leaving untouched", cm.getJsonName());
                continue;
            }
            TableMetadata child = registry.require(cm.getTableRef());
            String fkJson = child.jsonNameForColumn(cm.getChildKey());

            if (cm.isOneToOne()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) section;
                childMap.put(fkJson, pk);
                throwIfInvalid(child, childMap);
                log.debug("Upsert child {} (1:1) for parent pk={}", child.getName(), pk);
                repo.upsert(child.getName(), childMap);
            } else { // ONE_TO_MANY
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) section;
                if (list.isEmpty()) {
                    log.debug("Empty child list — deleting all {} rows for parent pk={}",
                            child.getName(), pk);
                    repo.deleteByFk(child.getName(), cm.getChildKey(), pk);
                } else {
                    List<Object> keepIds = new ArrayList<>(list.size());
                    String idJson = cm.getIdJsonName();
                    for (Map<String, Object> row : list) {
                        if (cm.isGenerateIdIfMissing() && idJson != null) {
                            Object id = row.get(idJson);
                            if (id == null || (id instanceof String s && s.isBlank())) {
                                row.put(idJson, UUID.randomUUID().toString());
                            }
                        }
                        row.put(fkJson, pk);
                        throwIfInvalid(child, row);
                        if (idJson != null) keepIds.add(row.get(idJson));
                    }
                    log.debug("Merge {} child rows for parent pk={}", list.size(), pk);
                    repo.mergeAll(child.getName(), list);
                    if (!keepIds.isEmpty()) {
                        repo.deleteByFkNotIn(child.getName(), cm.getChildKey(), pk, keepIds);
                    }
                }
            }
        }

        return findByPk(parentAlias, pk)
                .orElseThrow(() -> new IllegalStateException(
                        "Upsert succeeded but findByPk returned empty for pk=" + pk));
    }

    // ──────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findByPk(String parentAlias, Object pk) {
        TableMetadata parent = registry.require(parentAlias);
        return repo.findByPk(parent.getName(), pk).map(parentRow -> {
            for (ChildMetadata cm : parent.getChildren()) {
                TableMetadata child = registry.require(cm.getTableRef());
                if (cm.isOneToOne()) {
                    parentRow.put(cm.getJsonName(),
                            repo.findByFk(child.getName(), cm.getChildKey(), pk).orElse(null));
                } else {
                    parentRow.put(cm.getJsonName(),
                            repo.findAllByFk(child.getName(), cm.getChildKey(), pk));
                }
            }
            return parentRow;
        });
    }

    @Transactional(readOnly = true)
    public PagedResponse<Map<String, Object>> findAll(String parentAlias, int page, int size) {
        TableMetadata parent = registry.require(parentAlias);
        int offset = page * size;
        List<Map<String, Object>> content = repo.findPage(parent.getName(), offset, size);
        long total = repo.count(parent.getName());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PagedResponse.<Map<String, Object>>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────

    @Transactional
    public void delete(String parentAlias, Object pk) {
        TableMetadata parent = registry.require(parentAlias);
        repo.findByPk(parent.getName(), pk)
                .orElseThrow(() -> new ResourceNotFoundException(parent.getName(), String.valueOf(pk)));

        // FK has no ON DELETE CASCADE — delete children first
        for (ChildMetadata cm : parent.getChildren()) {
            TableMetadata child = registry.require(cm.getTableRef());
            int deleted = repo.deleteByFk(child.getName(), cm.getChildKey(), pk);
            log.debug("Deleted {} rows from {} for parent pk={}", deleted, child.getName(), pk);
        }
        repo.deleteByPk(parent.getName(), pk);
        log.info("Deleted parent {} pk={}", parent.getName(), pk);
    }

    // ──────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────

    private Map<String, Object> parentPayloadOnly(TableMetadata parent, Map<String, Object> payload) {
        Set<String> childJsonNames = new HashSet<>();
        for (ChildMetadata cm : parent.getChildren()) {
            childJsonNames.add(cm.getJsonName());
        }
        Map<String, Object> copy = new HashMap<>(payload);
        copy.keySet().removeAll(childJsonNames);
        return copy;
    }

    private void throwIfInvalid(TableMetadata t, Map<String, Object> payload) {
        Map<String, String> errors = validator.validate(t, payload);
        if (!errors.isEmpty()) {
            throw new MetadataValidationException(errors);
        }
    }
}
