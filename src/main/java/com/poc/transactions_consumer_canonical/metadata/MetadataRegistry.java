package com.poc.transactions_consumer_canonical.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Loads every YAML file under {@code classpath:metadata/*.yaml} at startup,
 * deserializes into {@link TableMetadata}, validates each, and exposes lookups
 * by table name and by URL alias.
 * <p>
 * Fail-fast: any invalid file aborts startup (better than silent corruption later).
 */
@Slf4j
@Component
public class MetadataRegistry {

    private static final String METADATA_GLOB = "classpath:metadata/*.yaml";
    private static final String METADATA_GLOB_YML = "classpath:metadata/*.yml";

    /** Dedicated YAML ObjectMapper — never use the web-layer mapper here. */
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, TableMetadata> byTable = new HashMap<>();
    private final Map<String, TableMetadata> byAlias = new HashMap<>();

    @PostConstruct
    public void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] files = resolver.getResources(METADATA_GLOB);
        Resource[] ymlFiles = resolver.getResources(METADATA_GLOB_YML);

        int total = files.length + ymlFiles.length;
        if (total == 0) {
            log.warn("No metadata YAML files found under classpath:metadata/. "
                    + "Generic endpoints will return 404 for every alias.");
            return;
        }

        for (Resource r : files)     loadOne(r);
        for (Resource r : ymlFiles)  loadOne(r);

        // Second pass: validate child table references resolve
        for (TableMetadata t : byTable.values()) {
            for (ChildMetadata cm : t.getChildren()) {
                TableMetadata child = byTable.get(cm.getTableRef().toUpperCase(Locale.ROOT));
                if (child == null) {
                    throw new IllegalStateException("Table " + t.getName()
                            + " child '" + cm.getJsonName()
                            + "' references unknown table: " + cm.getTableRef());
                }
                // verify childKey column exists on child
                if (child.columnByDbColumn(cm.getChildKey()).isEmpty()) {
                    throw new IllegalStateException("Table " + t.getName()
                            + " child '" + cm.getJsonName() + "' childKey "
                            + cm.getChildKey() + " is not a column of " + child.getName());
                }
            }
        }

        log.info("MetadataRegistry loaded {} tables: {}", byTable.size(), byTable.keySet());
    }

    private void loadOne(Resource r) throws IOException {
        TableMetadata t;
        try {
            t = yaml.readValue(r.getInputStream(), TableMetadata.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse metadata YAML " + r.getFilename(), ex);
        }
        try {
            t.validate();
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Invalid metadata YAML " + r.getFilename() + ": " + ex.getMessage(), ex);
        }
        TableMetadata clash = byTable.put(t.getName().toUpperCase(Locale.ROOT), t);
        if (clash != null) {
            throw new IllegalStateException("Duplicate table metadata for " + t.getName());
        }
        TableMetadata clashAlias = byAlias.put(t.getAlias().toLowerCase(Locale.ROOT), t);
        if (clashAlias != null) {
            throw new IllegalStateException("Duplicate alias '" + t.getAlias()
                    + "' between tables " + clashAlias.getName() + " and " + t.getName());
        }
        log.debug("Loaded metadata: table={} alias={} columns={} children={}",
                t.getName(), t.getAlias(), t.getColumns().size(), t.getChildren().size());
    }

    /**
     * Look up a table by either DB name (e.g. "SEND_TRANSACTIONS") or
     * URL alias (e.g. "send-transactions"). Throws if not found.
     */
    public TableMetadata require(String tableOrAlias) {
        Objects.requireNonNull(tableOrAlias, "tableOrAlias");
        TableMetadata t = byTable.get(tableOrAlias.toUpperCase(Locale.ROOT));
        if (t == null) t = byAlias.get(tableOrAlias.toLowerCase(Locale.ROOT));
        if (t == null) {
            throw new IllegalArgumentException("No table or alias matches: " + tableOrAlias);
        }
        return t;
    }

    public Collection<TableMetadata> all() {
        return Collections.unmodifiableCollection(byTable.values());
    }
}
