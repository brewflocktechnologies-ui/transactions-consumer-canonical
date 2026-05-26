package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenericTableRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private GenericTableRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        MetadataRegistry registry = mock(MetadataRegistry.class);
        SqlBuilder sqlBuilder = new SqlBuilder();
        Converters converters = new Converters(new ObjectMapper());

        TableMetadata table = TableMetadata.builder()
                .name("T").schema("S").alias("t").pk("ID").pkJsonName("id")
                .defaultOrderBy("ID")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("id").dbColumn("ID").sqlType("VARCHAR")
                                .pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("name").dbColumn("NAME").sqlType("VARCHAR").build(),
                        ColumnMetadata.builder().jsonName("crteTs").dbColumn("CRTE_TS").sqlType("TIMESTAMP")
                                .audit(true).readOnly(true).build()
                )).build();
        table.validate();
        when(registry.require(anyString())).thenReturn(table);

        repo = new GenericTableRepository(jdbc, registry, sqlBuilder, converters);
    }

    @Test
    void upsert_bindsNonAuditColumns_andRunsMerge() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "X-1");
        payload.put("name", "Alice");

        repo.upsert("t", payload);

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        assertThat(params.getValue().getValue("id")).isEqualTo("X-1");
        assertThat(params.getValue().getValue("name")).isEqualTo("Alice");
        // audit columns are skipped during bind
        assertThat(params.getValue().getValues()).doesNotContainKey("crteTs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByPk_present() {
        Map<String, Object> row = Map.of("id", "X-1");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(row);

        Optional<Map<String, Object>> out = repo.findByPk("t", "X-1");
        assertThat(out).hasValue(row);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByPk_absent_returnsEmpty() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(repo.findByPk("t", "MISSING")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByFk_presentAndAbsent() {
        Map<String, Object> row = Map.of("id", "X");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(row);
        assertThat(repo.findByFk("t", "PARENT_ID", "P-1")).contains(row);

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(repo.findByFk("t", "PARENT_ID", "P-1")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllByFk_delegates() {
        List<Map<String, Object>> rows = List.of(Map.of("id", "A"), Map.of("id", "B"));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(rows);

        assertThat(repo.findAllByFk("t", "PARENT_ID", "P-1")).hasSize(2);
    }
}
