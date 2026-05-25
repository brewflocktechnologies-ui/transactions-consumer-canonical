package com.poc.transactions_consumer_canonical.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.mapper.SendTransactionRowMapper;
import com.poc.transactions_consumer_canonical.metadata.ColumnMetadata;
import com.poc.transactions_consumer_canonical.metadata.MetadataRegistry;
import com.poc.transactions_consumer_canonical.metadata.TableMetadata;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendTransactionRepositoryImplTest {

    private NamedParameterJdbcTemplate jdbc;
    private SendTransactionRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        SendTransactionRowMapper rowMapper = mock(SendTransactionRowMapper.class);
        MetadataRegistry registry = mock(MetadataRegistry.class);
        TableMetadata table = TableMetadata.builder()
                .name("SEND_TRANSACTIONS").alias("send-transactions")
                .pk("TRAN_ID").pkJsonName("tranId")
                .columns(List.of(
                        ColumnMetadata.builder().jsonName("tranId").dbColumn("TRAN_ID")
                                .sqlType("VARCHAR").pk(true).nullGuard(false).build(),
                        ColumnMetadata.builder().jsonName("tranType").dbColumn("TRAN_TYPE")
                                .sqlType("VARCHAR").required(true).build(),
                        ColumnMetadata.builder().jsonName("tranAmt").dbColumn("TRAN_AMT").sqlType("NUMERIC").build()
                )).build();
        table.validate();
        when(registry.require("SEND_TRANSACTIONS")).thenReturn(table);

        repo = new SendTransactionRepositoryImpl(jdbc, rowMapper, registry, new SqlBuilder(),
                new Converters(new ObjectMapper()),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void upsert_bindsScalarFieldsFromModel() {
        SendTransaction t = SendTransaction.builder()
                .tranId("X-1").tranType("SEND").tranAmt(new BigDecimal("100.00"))
                .build();
        repo.upsert(t);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), params.capture());
        assertThat(params.getValue().getValue("tranId")).isEqualTo("X-1");
        assertThat(params.getValue().getValue("tranType")).isEqualTo("SEND");
        assertThat(params.getValue().getValue("tranAmt")).isInstanceOf(BigDecimal.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_present() {
        SendTransaction model = SendTransaction.builder().tranId("X-1").build();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(model);
        assertThat(repo.findById("X-1")).contains(model);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_missing_returnsEmpty() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(repo.findById("MISSING")).isEmpty();
    }
}
