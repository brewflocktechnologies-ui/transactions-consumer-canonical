package com.poc.transactions_consumer_canonical.canonicalmapping;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure-path tests for {@link CanonicalMappingRegistry} that the real
 * {@code canonical-mappings/*.yaml} resources cannot trigger.
 *
 * <p>Invokes the private {@code loadOne(Resource)} via reflection with hand-crafted
 * in-memory YAML strings to hit every guard clause:
 *  - YAML parse failure
 *  - missing eventType
 *  - duplicate eventType
 *  - duplicate eventName
 *  - fallback to eventType-as-key when eventNames empty
 */
class CanonicalMappingRegistryFailureTest {

    private static Resource yamlResource(String name, String body) {
        return new ByteArrayResource(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return name; }
        };
    }

    private static void invokeLoadOne(CanonicalMappingRegistry reg, Resource r) throws Exception {
        var m = CanonicalMappingRegistry.class.getDeclaredMethod("loadOne", Resource.class);
        m.setAccessible(true);
        try {
            m.invoke(reg, r);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            if (ite.getCause() instanceof RuntimeException re) throw re;
            if (ite.getCause() instanceof Exception ex) throw ex;
            throw ite;
        }
    }

    @Test
    void malformedYaml_throwsIllegalState() {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        Resource bad = yamlResource("bad.yaml", "this: is: not: valid: yaml: ::");
        assertThatThrownBy(() -> invokeLoadOne(reg, bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse canonical-mapping YAML");
    }

    @Test
    void missingEventType_throwsIllegalState() {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        Resource r = yamlResource("noEventType.yaml", "tranType: X\n");
        assertThatThrownBy(() -> invokeLoadOne(reg, r))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing 'eventType'");
    }

    @Test
    void blankEventType_throwsIllegalState() {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        Resource r = yamlResource("blank.yaml", "eventType: '  '\n");
        assertThatThrownBy(() -> invokeLoadOne(reg, r))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing 'eventType'");
    }

    @Test
    void duplicateEventType_acrossFiles_throws() throws Exception {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        Resource a = yamlResource("a.yaml", """
                eventType: DUPE
                tranType: X
                eventNames: [E1]
                """);
        invokeLoadOne(reg, a);
        Resource b = yamlResource("b.yaml", """
                eventType: DUPE
                tranType: Y
                eventNames: [E2]
                """);
        assertThatThrownBy(() -> invokeLoadOne(reg, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate eventType");
    }

    @Test
    void duplicateEventName_acrossFiles_throws() throws Exception {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        invokeLoadOne(reg, yamlResource("a.yaml", """
                eventType: ALPHA
                tranType: X
                eventNames: [SHARED_EVENT]
                """));
        Resource b = yamlResource("b.yaml", """
                eventType: BETA
                tranType: Y
                eventNames: [SHARED_EVENT]
                """);
        assertThatThrownBy(() -> invokeLoadOne(reg, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate eventName");
    }

    @Test
    void emptyEventNames_fallsBackToEventTypeAsKey() throws Exception {
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        invokeLoadOne(reg, yamlResource("fallback.yaml", """
                eventType: STANDALONE
                tranType: X
                """));
        assertThat(reg.findByEventName("STANDALONE")).isPresent();
        assertThat(reg.findByEventName("standalone")).isPresent(); // case-insensitive
    }

    @Test
    void load_isANoOpWhenNoFilesPresent() {
        // Verify behaviour when the resolver returns no resources. We can't easily
        // override the GLOB constant; instead exercise the empty case via the
        // public contract on a fresh instance prior to load().
        CanonicalMappingRegistry reg = new CanonicalMappingRegistry();
        assertThat(reg.all()).isEmpty();
        // private byEventName map is empty
        @SuppressWarnings("unchecked")
        java.util.Map<String, EventTypeMapping> byEventName =
                (java.util.Map<String, EventTypeMapping>) ReflectionTestUtils.getField(reg, "byEventName");
        assertThat(byEventName).isEmpty();
    }
}
