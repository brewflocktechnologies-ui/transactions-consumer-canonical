package com.poc.transactions_consumer_canonical.repository;

import com.poc.transactions_consumer_canonical.mapper.SendRecipDtlRowMapper;
import com.poc.transactions_consumer_canonical.mapper.SendTranAddrDtlRowMapper;
import com.poc.transactions_consumer_canonical.mapper.SendTranDtlRowMapper;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TypedChildRepositoryImplsTest {

    private NamedParameterJdbcTemplate jdbc;
    private SqlQueries queries;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        queries = mock(SqlQueries.class);
        when(queries.sendTranDtlUpsert()).thenReturn("MERGE TRAN_DTL");
        when(queries.sendTranDtlSelectByTranId()).thenReturn("SELECT TRAN_DTL");
        when(queries.sendRecipDtlUpsert()).thenReturn("MERGE RECIP_DTL");
        when(queries.sendRecipDtlSelectByTranId()).thenReturn("SELECT RECIP_DTL");
        when(queries.sendTranAddrDtlMerge()).thenReturn("MERGE ADDR");
        when(queries.sendTranAddrDtlSelectByTranId()).thenReturn("SELECT ADDR");
        when(queries.sendTranAddrDtlDeleteByTranId()).thenReturn("DELETE ADDR");
        when(queries.sendTranAddrDtlDeleteByTranIdNotIn()).thenReturn("DELETE ADDR NOT IN");
    }

    // ── SendTranDtlRepositoryImpl ─────────────────────────────────────────

    @Test
    void tranDtl_upsert_bindsTranIdAndPaymtRef() {
        SendTranDtlRepositoryImpl repo = new SendTranDtlRepositoryImpl(jdbc, mock(SendTranDtlRowMapper.class), queries);
        SendTranDtl d = SendTranDtl.builder().tranId("X-1").paymtRef("PR-1").acqIca(99L).build();
        repo.upsert(d);
        ArgumentCaptor<MapSqlParameterSource> cap = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(eq("MERGE TRAN_DTL"), cap.capture());
        assertThat(cap.getValue().getValue("tranId")).isEqualTo("X-1");
        assertThat(cap.getValue().getValue("paymtRef")).isEqualTo("PR-1");
        assertThat(cap.getValue().getValue("acqIca")).isEqualTo(99L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tranDtl_findByTranId_presentAndAbsent() {
        SendTranDtlRowMapper rm = mock(SendTranDtlRowMapper.class);
        SendTranDtlRepositoryImpl repo = new SendTranDtlRepositoryImpl(jdbc, rm, queries);
        SendTranDtl model = SendTranDtl.builder().tranId("X-1").build();
        when(jdbc.queryForObject(eq("SELECT TRAN_DTL"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(model);
        assertThat(repo.findByTranId("X-1")).contains(model);

        when(jdbc.queryForObject(eq("SELECT TRAN_DTL"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(repo.findByTranId("M")).isEmpty();
    }

    // ── SendRecipDtlRepositoryImpl ─────────────────────────────────────────

    @Test
    void recipDtl_upsert_bindsTranIdAndName() {
        SendRecipDtlRepositoryImpl repo = new SendRecipDtlRepositoryImpl(jdbc, mock(SendRecipDtlRowMapper.class), queries);
        SendRecipDtl r = SendRecipDtl.builder().tranId("X-1").sendFirstNam("Alice").build();
        repo.upsert(r);
        ArgumentCaptor<MapSqlParameterSource> cap = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(eq("MERGE RECIP_DTL"), cap.capture());
        assertThat(cap.getValue().getValue("tranId")).isEqualTo("X-1");
        assertThat(cap.getValue().getValue("sendFirstNam")).isEqualTo("Alice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recipDtl_findByTranId_presentAndAbsent() {
        SendRecipDtlRowMapper rm = mock(SendRecipDtlRowMapper.class);
        SendRecipDtlRepositoryImpl repo = new SendRecipDtlRepositoryImpl(jdbc, rm, queries);
        SendRecipDtl model = SendRecipDtl.builder().tranId("X").build();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(model);
        assertThat(repo.findByTranId("X")).contains(model);

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThat(repo.findByTranId("M")).isEmpty();
    }

    // ── SendTranAddrDtlRepositoryImpl ─────────────────────────────────────

    @Test
    void addrDtl_mergeAll_skipsEmptyAndNull() {
        SendTranAddrDtlRepositoryImpl repo = new SendTranAddrDtlRepositoryImpl(jdbc, mock(SendTranAddrDtlRowMapper.class), queries);
        repo.mergeAll(null);
        repo.mergeAll(List.of());
        verify(jdbc, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void addrDtl_mergeAll_batchesNonEmpty() {
        SendTranAddrDtlRepositoryImpl repo = new SendTranAddrDtlRepositoryImpl(jdbc, mock(SendTranAddrDtlRowMapper.class), queries);
        repo.mergeAll(List.of(
                SendTranAddrDtl.builder().id("A-1").tranId("X").addrType("SENDER").build(),
                SendTranAddrDtl.builder().id("A-2").tranId("X").addrType("RECIPIENT").build()));
        ArgumentCaptor<SqlParameterSource[]> cap = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc).batchUpdate(eq("MERGE ADDR"), cap.capture());
        assertThat(cap.getValue()).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addrDtl_findByTranId_delegates() {
        SendTranAddrDtlRepositoryImpl repo = new SendTranAddrDtlRepositoryImpl(jdbc, mock(SendTranAddrDtlRowMapper.class), queries);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(SendTranAddrDtl.builder().id("A").build()));
        assertThat(repo.findByTranId("X")).hasSize(1);
    }

    @Test
    void addrDtl_deleteByTranId_invokesUpdate() {
        SendTranAddrDtlRepositoryImpl repo = new SendTranAddrDtlRepositoryImpl(jdbc, mock(SendTranAddrDtlRowMapper.class), queries);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(2);
        repo.deleteByTranId("X");
        verify(jdbc).update(eq("DELETE ADDR"), any(MapSqlParameterSource.class));
    }

    @Test
    void addrDtl_deleteByTranIdNotIn_invokesUpdate() {
        SendTranAddrDtlRepositoryImpl repo = new SendTranAddrDtlRepositoryImpl(jdbc, mock(SendTranAddrDtlRowMapper.class), queries);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        repo.deleteByTranIdNotIn("X", List.of("KEEP-1"));
        verify(jdbc).update(eq("DELETE ADDR NOT IN"), any(MapSqlParameterSource.class));
    }
}
