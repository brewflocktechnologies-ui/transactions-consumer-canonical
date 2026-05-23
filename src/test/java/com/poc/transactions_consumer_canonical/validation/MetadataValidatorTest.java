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
}
