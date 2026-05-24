package com.poc.transactions_consumer_canonical.canonicalmapping;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads every YAML file under {@code classpath:canonical-mappings/*.yaml} at startup
 * and builds an index keyed by each event name the mapping declares.
 *
 * <p>Adding support for a new event type requires <em>only</em> dropping a new
 * {@code .yaml} file into {@code src/main/resources/canonical-mappings/} —
 * no Java code changes are needed.
 *
 * <p>Routing logic:
 * <ol>
 *   <li>If the YAML declares {@code eventNames}, every name in that list is
 *       registered as a lookup key.</li>
 *   <li>If {@code eventNames} is absent or empty, the {@code eventType} value
 *       itself is used as the sole key.</li>
 * </ol>
 */
@Slf4j
@Component
public class CanonicalMappingRegistry {

    private static final String GLOB = "classpath:canonical-mappings/*.yaml";

    /** Dedicated YAML ObjectMapper — never shared with the web layer. */
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** eventName (upper-cased) → EventTypeMapping */
    private final Map<String, EventTypeMapping> byEventName = new HashMap<>();

    /** eventType (upper-cased) → EventTypeMapping  (for admin / logging) */
    private final Map<String, EventTypeMapping> byEventType = new HashMap<>();

    @PostConstruct
    public void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] files = resolver.getResources(GLOB);

        if (files.length == 0) {
            log.warn("[CanonicalMappingRegistry] No mapping YAML files found under {}. "
                    + "All Kafka events will be unroutable.", GLOB);
            return;
        }

        for (Resource r : files) {
            loadOne(r);
        }

        log.info("[CanonicalMappingRegistry] Loaded {} event-type mapping(s): {}",
                byEventType.size(), byEventType.keySet());
        log.info("[CanonicalMappingRegistry] Registered {} event-name route(s): {}",
                byEventName.size(), byEventName.keySet());
    }

    private void loadOne(Resource resource) throws IOException {
        EventTypeMapping mapping;
        try {
            mapping = yaml.readValue(resource.getInputStream(), EventTypeMapping.class);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to parse canonical-mapping YAML: " + resource.getFilename(), ex);
        }

        if (mapping.getEventType() == null || mapping.getEventType().isBlank()) {
            throw new IllegalStateException(
                    "canonical-mapping YAML is missing 'eventType': " + resource.getFilename());
        }

        String typeKey = mapping.getEventType().toUpperCase(Locale.ROOT);
        if (byEventType.containsKey(typeKey)) {
            throw new IllegalStateException(
                    "Duplicate eventType '" + typeKey + "' in " + resource.getFilename());
        }
        byEventType.put(typeKey, mapping);

        // Register each declared event name as a route key
        List<String> names = mapping.getEventNames();
        if (names == null || names.isEmpty()) {
            // Fall back: use the eventType itself as the route key
            register(typeKey, mapping, resource.getFilename());
        } else {
            for (String name : names) {
                register(name.toUpperCase(Locale.ROOT), mapping, resource.getFilename());
            }
        }

        log.debug("[CanonicalMappingRegistry] Loaded eventType={} from {}",
                mapping.getEventType(), resource.getFilename());
    }

    private void register(String eventName, EventTypeMapping mapping, String filename) {
        EventTypeMapping existing = byEventName.put(eventName, mapping);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate eventName '" + eventName + "' registered by both '"
                            + existing.getEventType() + "' and '"
                            + mapping.getEventType() + "' (file: " + filename + ")");
        }
    }

    /**
     * Looks up the mapping for a given {@code EventEnvelope.eventName}.
     * The comparison is case-insensitive.
     *
     * @param eventName value from {@code EventEnvelope.eventName}
     * @return the matching mapping, or {@link Optional#empty()} if none registered
     */
    public Optional<EventTypeMapping> findByEventName(String eventName) {
        if (eventName == null) return Optional.empty();
        return Optional.ofNullable(byEventName.get(eventName.toUpperCase(Locale.ROOT)));
    }

    /** Returns all loaded event-type mappings (unmodifiable). */
    public Collection<EventTypeMapping> all() {
        return Collections.unmodifiableCollection(byEventType.values());
    }
}
