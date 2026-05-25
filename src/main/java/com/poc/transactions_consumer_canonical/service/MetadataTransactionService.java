package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.metadata.ChildMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only orchestrator for the metadata-driven (v2) surface.
 *
 * <p>All write operations (upsert, delete) have been moved to the Kafka
 * canonical pipeline. The only remaining responsibility here is to fetch
 * a parent row with its full child graph for the read-only REST endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataTransactionService {

    private final MetadataRegistry registry;
    private final GenericTableRepository repo;

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
}
