package com.poc.transactions_consumer_canonical.validation;

import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataValidatorTest {

    private final MetadataValidator v = new MetadataValidator();

    private TableMetadata table() {
        return TableMetadata.builder()
                .name("T1").alias("t1").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).required(true).maxLength(50).build(),
                        ColumnMetadata.builder().jsonName("name").dbColumn("NAME").sqlType("VARCHAR")
                                .maxLength(10).build(),
                        ColumnMetadata.builder().jsonName("code").dbColumn("CODE").sqlType("VARCHAR")
                                .pattern("^[A-Z]{3}$").build(),
                        ColumnMetadata.builder().jsonName("crteTs").dbColumn("CRTE_TS").sqlType("TIMESTAMP")
                                .audit(true).readOnly(true).build()
                )).build();
    }

    @Test
    void valid_payload_returns_empty_errors() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("name", "Short");
        p.put("code", "USD");
        assertTrue(v.validate(table(), p).isEmpty());
    }

    @Test
    void missing_required_field_reports_must_not_be_null() {
        Map<String, Object> p = Map.of("name", "X");
        Map<String, String> errs = v.validate(table(), p);
        assertEquals("must not be null", errs.get("id"));
    }

    @Test
    void value_exceeds_max_length_reports_error() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("name", "This name is way too long for the limit");
        Map<String, String> errs = v.validate(table(), p);
        assertTrue(errs.get("name").contains("must not exceed 10 characters"));
    }

    @Test
    void value_failing_pattern_reports_error() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("code", "usd");
        Map<String, String> errs = v.validate(table(), p);
        assertEquals("does not match required pattern", errs.get("code"));
    }

    @Test
    void audit_and_readOnly_columns_are_not_validated() {
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        // crteTs is audit/readOnly — its value (or absence) must be irrelevant
        assertTrue(v.validate(table(), p).isEmpty());
    }

    @Test
    void null_payload_returns_body_required_error() {
        Map<String, String> errs = v.validate(table(), null);
        assertEquals("request body is required", errs.get("_body"));
    }

    @Test
    void non_string_value_skips_string_constraints() {
        // Integer value: not a String, so validateStringConstraints is never called
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("name", 42); // Integer, not String
        assertTrue(v.validate(table(), p).isEmpty());
    }

    @Test
    void column_with_null_jsonName_is_skipped() {
        TableMetadata t = TableMetadata.builder()
                .name("T1").alias("t1").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        // jsonName intentionally null — should be silently skipped
                        ColumnMetadata.builder().dbColumn("INTERNAL").sqlType("VARCHAR")
                                .required(true).build()))
                .build();
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        // no "internal" key — but column has null jsonName so it's skipped
        assertTrue(v.validate(t, p).isEmpty());
    }

    @Test
    void readOnly_non_audit_column_is_skipped_entirely() {
        TableMetadata t = TableMetadata.builder()
                .name("T1").alias("t1").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        // readOnly:true but audit:false — should be skipped (not validated)
                        ColumnMetadata.builder().jsonName("computed").dbColumn("COMPUTED")
                                .sqlType("VARCHAR").readOnly(true).required(true).build()))
                .build();
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        // "computed" absent from payload; required:true but readOnly skips it
        assertTrue(v.validate(t, p).isEmpty());
    }

    @Test
    void blank_pattern_string_is_not_applied() {
        TableMetadata t = TableMetadata.builder()
                .name("T1").alias("t1").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("tag").dbColumn("TAG").sqlType("VARCHAR")
                                .pattern("   ").build())) // blank pattern → not applied
                .build();
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("tag", "anything goes");
        assertTrue(v.validate(t, p).isEmpty());
    }

    @Test
    void string_with_null_maxLength_does_not_fail_length_check() {
        TableMetadata t = TableMetadata.builder()
                .name("T1").alias("t1").pk("ID").pkJsonName("id")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        // maxLength is null (not set) — no length constraint applied
                        ColumnMetadata.builder().jsonName("desc").dbColumn("DESC").sqlType("VARCHAR").build()))
                .build();
        Map<String, Object> p = new HashMap<>();
        p.put("id", "X-001");
        p.put("desc", "A".repeat(500)); // very long but maxLength is null
        assertTrue(v.validate(t, p).isEmpty());
    }
}
