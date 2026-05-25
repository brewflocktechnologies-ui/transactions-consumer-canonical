package com.poc.transactions_consumer_canonical.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hits the loadOne() failure branches in MetadataRegistry by exercising the
 * private loader directly with in-memory ByteArrayResources. Avoids booting Spring.
 */
class MetadataRegistryLoadTest {

    private static final String VALID_T1 = """
            name: T1
            schema: S
            alias: t1
            pk: ID
            pkJsonName: id
            columns:
              - { jsonName: id,   dbColumn: ID,   sqlType: VARCHAR, pk: true, nullGuard: false }
            """;

    private static final Method LOAD_ONE = loadOneMethod();

    private static Method loadOneMethod() {
        try {
            Method m = MetadataRegistry.class.getDeclaredMethod("loadOne", Resource.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Resource yamlResource(String body, String name) {
        return new ByteArrayResource(body.getBytes()) {
            @Override public String getFilename() { return name; }
        };
    }

    /** Wraps loadOne so the lambda inside assertThatThrownBy has exactly one invocation. */
    private static Callable<Object> invokeLoadOne(MetadataRegistry registry, Resource resource) {
        return () -> {
            try {
                return LOAD_ONE.invoke(registry, resource);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof RuntimeException re) throw re;
                throw new IllegalStateException(ex.getCause());
            }
        };
    }

    @Test
    void loadOne_acceptsValidYaml_andRegistersByTableAndAlias() throws Exception {
        MetadataRegistry registry = new MetadataRegistry();
        LOAD_ONE.invoke(registry, yamlResource(VALID_T1, "t1.yaml"));
        Collection<TableMetadata> all = registry.all();
        assertThat(all).hasSize(1);
        assertThat(registry.require("T1").getName()).isEqualTo("T1");
        assertThat(registry.require("t1").getName()).isEqualTo("T1");
    }

    @Test
    void loadOne_rejectsDuplicateTableName() throws Exception {
        MetadataRegistry registry = new MetadataRegistry();
        LOAD_ONE.invoke(registry, yamlResource(VALID_T1, "first.yaml"));
        Resource dup = yamlResource(VALID_T1, "second.yaml");
        assertThatThrownBy(invokeLoadOne(registry, dup)::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate table metadata for T1");
    }

    @Test
    void loadOne_rejectsDuplicateAlias() throws Exception {
        MetadataRegistry registry = new MetadataRegistry();
        LOAD_ONE.invoke(registry, yamlResource(VALID_T1, "t1.yaml"));
        String sameAliasDifferentTable = """
                name: T2
                alias: t1
                pk: ID
                pkJsonName: id
                columns:
                  - { jsonName: id, dbColumn: ID, sqlType: VARCHAR, pk: true, nullGuard: false }
                """;
        Resource dup = yamlResource(sameAliasDifferentTable, "t2.yaml");
        assertThatThrownBy(invokeLoadOne(registry, dup)::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate alias 't1' between tables T1 and T2");
    }

    @Test
    void loadOne_invalidYaml_throwsIllegalState() {
        MetadataRegistry registry = new MetadataRegistry();
        Resource broken = yamlResource("name: T1\n  alias: : : :\n  bad: [unclosed", "broken.yaml");
        assertThatThrownBy(invokeLoadOne(registry, broken)::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse metadata YAML");
    }

    @Test
    void loadOne_validYamlButInvalidMetadata_throwsIllegalState() {
        MetadataRegistry registry = new MetadataRegistry();
        String missingPk = """
                name: BAD
                alias: bad
                pkJsonName: id
                columns:
                  - { jsonName: id, dbColumn: ID, sqlType: VARCHAR, pk: true, nullGuard: false }
                """;
        Resource bad = yamlResource(missingPk, "bad.yaml");
        assertThatThrownBy(invokeLoadOne(registry, bad)::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid metadata YAML bad.yaml");
    }

    @Test
    void require_unknownAlias_throws() {
        MetadataRegistry registry = new MetadataRegistry();
        assertThatThrownBy(() -> registry.require("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No table or alias matches");
    }

    @Test
    void require_nullArg_throwsNpe() {
        MetadataRegistry registry = new MetadataRegistry();
        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void all_isUnmodifiable() {
        MetadataRegistry registry = new MetadataRegistry();
        Collection<TableMetadata> all = registry.all();
        TableMetadata empty = TableMetadata.builder().build();
        assertThatThrownBy(() -> all.add(empty))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void load_runsAndPopulatesRegistry_fromClasspathMetadata() {
        MetadataRegistry registry = new MetadataRegistry();
        ReflectionTestUtils.invokeMethod(registry, "load");
        assertThat(registry.all()).isNotEmpty();
    }
}
