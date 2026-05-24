package com.poc.transactions_consumer_canonical.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java unit tests for the YAML deserialization + validation chain.
 * Does not boot Spring — fast feedback for metadata schema changes.
 */
class MetadataRegistryTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    private TableMetadata parse(String yamlString) throws Exception {
        return yaml.readValue(new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8)),
                TableMetadata.class);
    }

    @Test
    void valid_minimal_table_passes_validation() throws Exception {
        TableMetadata t = parse("""
                name: T1
                schema: S
                alias: t1
                pk: ID
                pkJsonName: id
                columns:
                  - { jsonName: id,   dbColumn: ID,   sqlType: VARCHAR, pk: true, nullGuard: false }
                  - { jsonName: name, dbColumn: NAME, sqlType: VARCHAR, maxLength: 50 }
                """);
        assertDoesNotThrow(t::validate);
        assertEquals("T1", t.getName());
        assertEquals(2, t.getColumns().size());
        assertEquals("id", t.pkColumn().getJsonName());
    }

    @ParameterizedTest
    @MethodSource("validationFailureProvider")
    void validation_fails_with_expected_message(String yamlString, String expectedFragment)
            throws Exception {
        TableMetadata t = parse(yamlString);
        IllegalStateException ex = assertThrows(IllegalStateException.class, t::validate);
        assertTrue(ex.getMessage().contains(expectedFragment), ex.getMessage());
    }

    static Stream<Arguments> validationFailureProvider() {
        return Stream.of(
                Arguments.of(
                        """
                        name: T1
                        alias: t1
                        pk: ID
                        pkJsonName: id
                        columns:
                          - { jsonName: name, dbColumn: NAME, sqlType: VARCHAR }
                        """,
                        "pk:true"
                ),
                Arguments.of(
                        """
                        name: T1
                        alias: t1
                        pk: ID
                        pkJsonName: id
                        columns:
                          - { jsonName: id,    dbColumn: ID,   sqlType: VARCHAR, pk: true }
                          - { jsonName: name1, dbColumn: NAME, sqlType: VARCHAR }
                          - { jsonName: name2, dbColumn: NAME, sqlType: VARCHAR }
                        """,
                        "duplicate dbColumn"
                ),
                Arguments.of(
                        """
                        name: T1
                        alias: t1
                        pk: ID
                        pkJsonName: id
                        columns:
                          - { jsonName: id,   dbColumn: ID,   sqlType: VARCHAR, pk: true, nullGuard: false }
                          - { jsonName: blob, dbColumn: BLOB, sqlType: CLOB, clob: true, nullGuard: true }
                        """,
                        "nullGuard:false"
                )
        );
    }

    @Test
    void unknown_sqlType_fails_validation() throws Exception {
        TableMetadata t = parse("""
                name: T1
                alias: t1
                pk: ID
                pkJsonName: id
                columns:
                  - { jsonName: id,    dbColumn: ID,    sqlType: VARCHAR, pk: true }
                  - { jsonName: weird, dbColumn: WEIRD, sqlType: BANANA }
                """);
        assertThrows(IllegalStateException.class, t::validate);
    }

    @Test
    void column_helpers_default_correctly() {
        ColumnMetadata c = ColumnMetadata.builder().sqlType("VARCHAR").build();
        assertTrue(c.isNullGuard(), "nullGuard defaults to true");
        assertFalse(c.isPk());
        assertFalse(c.isClob());
        assertFalse(c.isAudit());
        assertEquals("PASSTHROUGH", c.converterName());
        assertEquals(java.sql.Types.VARCHAR, c.jdbcType());

        ColumnMetadata clob = ColumnMetadata.builder().sqlType("CLOB").clob(true).build();
        assertEquals(java.sql.Types.VARCHAR, clob.jdbcType(),
                "CLOB columns must bind as VARCHAR — TO_CLOB() takes VARCHAR2");
    }

    @Test
    void jsonName_lookup_works() throws Exception {
        TableMetadata t = parse("""
                name: T1
                alias: t1
                pk: USER_ID
                pkJsonName: userId
                columns:
                  - { jsonName: userId,    dbColumn: USER_ID,    sqlType: VARCHAR, pk: true, nullGuard: false }
                  - { jsonName: firstName, dbColumn: FIRST_NAME, sqlType: VARCHAR }
                """);
        t.validate();
        assertEquals("userId", t.jsonNameForColumn("USER_ID"));
        assertEquals("firstName", t.jsonNameForColumn("first_name")); // case-insensitive
        assertThrows(IllegalStateException.class, () -> t.jsonNameForColumn("DOES_NOT_EXIST"));
    }
}
