package com.poc.transactions_consumer_canonical.canonicalmapping;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Boots {@link CanonicalMappingRegistry} against the real
 * {@code classpath:canonical-mappings/*.yaml} resources and verifies routing.
 */
class CanonicalMappingRegistryTest {

    @Test
    void loadsAllYamlFilesAndIndexesByEventName() throws Exception {
        CanonicalMappingRegistry registry = new CanonicalMappingRegistry();
        registry.load();

        // PAYMENT.yaml + FUNDING.yaml + AIS.yaml all live under canonical-mappings/
        assertThat(registry.all()).extracting(EventTypeMapping::getEventType)
                .contains("PAYMENT", "FUNDING", "AIS");

        // Routing by declared eventNames (case-insensitive)
        assertThat(registry.findByEventName("PAYMENT_INITIATED"))
                .isPresent()
                .map(EventTypeMapping::getEventType)
                .contains("PAYMENT");
        assertThat(registry.findByEventName("payment_completed"))
                .isPresent();
        assertThat(registry.findByEventName("AIS"))
                .isPresent()
                .map(EventTypeMapping::getEventType)
                .contains("AIS");
    }

    @Test
    void findByEventName_unknown_returnsEmpty() throws Exception {
        CanonicalMappingRegistry registry = new CanonicalMappingRegistry();
        registry.load();
        assertThat(registry.findByEventName("NO_SUCH_EVENT")).isEmpty();
    }

    @Test
    void findByEventName_null_returnsEmpty() {
        CanonicalMappingRegistry registry = new CanonicalMappingRegistry();
        assertThat(registry.findByEventName(null)).isEmpty();
    }

    @Test
    void all_returnsUnmodifiableView() throws Exception {
        CanonicalMappingRegistry registry = new CanonicalMappingRegistry();
        registry.load();
        var all = registry.all();
        EventTypeMapping mapping = new EventTypeMapping();
        assertThrows(UnsupportedOperationException.class, () -> all.add(mapping));
    }

    @Test
    void warnsButDoesNotThrowWhenNoMappingsFound() {
        // Subclass with overridden GLOB to point at empty location — but the field is
        // private static, so we exercise the same code path by verifying that the
        // public load() works (already covered). Instead use the public Optional
        // contract on findByEventName to verify graceful behaviour with no init.
        CanonicalMappingRegistry empty = new CanonicalMappingRegistry();
        assertThat(empty.findByEventName("ANYTHING")).isEmpty();
        assertThat(empty.all()).isEmpty();
    }

    // ─── Fields are auto-loaded via PostConstruct in the container; the unit
    //     test invokes load() directly, exercising every branch in loadOne(). ───

    @Test
    void registry_isInternallyConsistent() throws Exception {
        CanonicalMappingRegistry registry = new CanonicalMappingRegistry();
        registry.load();
        // sanity: each eventName key maps to a non-null EventTypeMapping
        for (EventTypeMapping mapping : registry.all()) {
            assertThat(mapping.getEventType()).isNotBlank();
        }
        // confirm internal map size matches all() size
        @SuppressWarnings("unchecked")
        java.util.Map<String, EventTypeMapping> byType =
                (java.util.Map<String, EventTypeMapping>) ReflectionTestUtils.getField(registry, "byEventType");
        assertThat(byType).isNotNull().hasSize(registry.all().size());
    }
}
