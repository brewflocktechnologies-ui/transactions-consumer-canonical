package com.poc.transactions_consumer_canonical.service;

import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearingEventServiceTest {

    private static final String TXN_ID       = "TXN-PAY-001";
    private static final String KEY_TRAN_ID  = "tranId";
    private static final String KEY_SETL_AMT = "setlAmt";
    private static final String VAL_SETL_AMT = "1500.00";

    private GenericTableRepository repo;
    private ClearingEventService service;

    @BeforeEach
    void setUp() {
        repo = mock(GenericTableRepository.class);
        service = new ClearingEventService(repo);
        // Default: parent SEND_TRANSACTIONS row exists.
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any()))
                .thenReturn(Optional.of(Map.of(KEY_TRAN_ID, TXN_ID)));
    }

    private Map<String, Object> minimalClearing() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clrgSt", "SETTLED");
        m.put("clrgDtTs", LocalDateTime.of(2026, 5, 26, 10, 0));
        return m;
    }

    // ── CLEARING ────────────────────────────────────────────────────────

    @Test
    void upsertClearing_serializesAndMerges() {
        Map<String, Object> payload = minimalClearing();
        payload.put("acqIcaRefTxt", "ACQ-REF-1");

        service.upsertClearing(TXN_ID, payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).upsert(eq(ClearingEventService.CLEARING_ALIAS), captor.capture());
        assertThat(captor.getValue()).containsEntry(KEY_TRAN_ID, TXN_ID);
        assertThat(captor.getValue()).containsEntry("clrgSt", "SETTLED");
        assertThat(captor.getValue()).containsEntry("acqIcaRefTxt", "ACQ-REF-1");
    }

    @Test
    void upsertClearing_nullTranId_throws() {
        Map<String, Object> payload = minimalClearing();
        assertThatThrownBy(() -> service.upsertClearing(null, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KEY_TRAN_ID);
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertClearing_blankTranId_throws() {
        Map<String, Object> payload = minimalClearing();
        assertThatThrownBy(() -> service.upsertClearing("   ", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KEY_TRAN_ID);
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertClearing_noParentRow_isLoggedAndSkipped() {
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any())).thenReturn(Optional.empty());

        service.upsertClearing(TXN_ID, minimalClearing());

        verify(repo, never()).upsert(eq(ClearingEventService.CLEARING_ALIAS), any());
    }

    @Test
    void upsertClearing_nullPayload_seedsOnlyTranId() {
        service.upsertClearing(TXN_ID, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).upsert(eq(ClearingEventService.CLEARING_ALIAS), captor.capture());
        assertThat(captor.getValue()).containsEntry(KEY_TRAN_ID, TXN_ID);
        assertThat(captor.getValue()).hasSize(1);
    }

    // ── SETTLEMENT ──────────────────────────────────────────────────────

    @Test
    void upsertSettlement_serializesAndMerges() {
        Map<String, Object> payload = minimalClearing();
        payload.put("setlDt", LocalDate.of(2026, 5, 26));
        payload.put(KEY_SETL_AMT, new BigDecimal(VAL_SETL_AMT));
        payload.put("setlCurrCd", "USD");

        service.upsertSettlement(TXN_ID, payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).upsert(eq(ClearingEventService.CLEARING_ALIAS), captor.capture());
        assertThat(captor.getValue()).containsEntry("setlCurrCd", "USD");
        assertThat(captor.getValue()).containsEntry(KEY_SETL_AMT, new BigDecimal(VAL_SETL_AMT));
    }

    @Test
    void upsertSettlement_missingTranId_throws() {
        Map<String, Object> p = new HashMap<>();
        p.put(KEY_SETL_AMT, new BigDecimal(VAL_SETL_AMT));
        assertThatThrownBy(() -> service.upsertSettlement("", p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(KEY_TRAN_ID);
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertSettlement_noParentRow_isLoggedAndSkipped() {
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any())).thenReturn(Optional.empty());

        service.upsertSettlement(TXN_ID, minimalClearing());

        verify(repo, never()).upsert(eq(ClearingEventService.CLEARING_ALIAS), any());
    }

    @Test
    void aliases_matchYamlMetadata() {
        assertThat(ClearingEventService.CLEARING_ALIAS).isEqualTo("send-tran-clrg-setlmt");
        assertThat(ClearingEventService.PARENT_ALIAS).isEqualTo("send-transactions");
    }
}
