package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.dto.SendTranClrgSetlmtRequest;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private GenericTableRepository repo;
    private ClearingEventService service;

    @BeforeEach
    void setUp() {
        repo = mock(GenericTableRepository.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ClearingEventService(repo, mapper);
        // Default: parent SEND_TRANSACTIONS row exists.
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any()))
                .thenReturn(Optional.of(Map.of("tranId", "TXN-PAY-001")));
    }

    private SendTranClrgSetlmtRequest minimalClearing() {
        SendTranClrgSetlmtRequest r = new SendTranClrgSetlmtRequest();
        r.setTranId("TXN-PAY-001");
        r.setClrgSt("SETTLED");
        r.setClrgDtTs(LocalDateTime.of(2026, 5, 26, 10, 0));
        return r;
    }

    // ── CLEARING ────────────────────────────────────────────────────────

    @Test
    void upsertClearing_serializesAndMerges() {
        SendTranClrgSetlmtRequest req = minimalClearing();
        req.setAcqIcaRefTxt("ACQ-REF-1");

        service.upsertClearing(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).upsert(eq(ClearingEventService.CLEARING_ALIAS), captor.capture());
        assertThat(captor.getValue()).containsEntry("tranId", "TXN-PAY-001");
        assertThat(captor.getValue()).containsEntry("clrgSt", "SETTLED");
        assertThat(captor.getValue()).containsEntry("acqIcaRefTxt", "ACQ-REF-1");
    }

    @Test
    void upsertClearing_nullRequest_throws() {
        assertThatThrownBy(() -> service.upsertClearing(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tranId");
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertClearing_blankTranId_throws() {
        SendTranClrgSetlmtRequest req = minimalClearing();
        req.setTranId("   ");
        assertThatThrownBy(() -> service.upsertClearing(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tranId");
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertClearing_noParentRow_isLoggedAndSkipped() {
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any())).thenReturn(Optional.empty());

        service.upsertClearing(minimalClearing());

        verify(repo, never()).upsert(eq(ClearingEventService.CLEARING_ALIAS), any());
    }

    // ── SETTLEMENT ──────────────────────────────────────────────────────

    @Test
    void upsertSettlement_serializesAndMerges() {
        SendTranClrgSetlmtRequest req = minimalClearing();
        req.setSetlDt(LocalDate.of(2026, 5, 26));
        req.setSetlAmt(new BigDecimal("1500.00"));
        req.setSetlCurrCd("USD");

        service.upsertSettlement(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).upsert(eq(ClearingEventService.CLEARING_ALIAS), captor.capture());
        assertThat(captor.getValue()).containsEntry("setlCurrCd", "USD");
        // BigDecimal serialises to number; assert it's present non-null
        assertThat(captor.getValue().get("setlAmt")).isNotNull();
    }

    @Test
    void upsertSettlement_missingTranId_throws() {
        SendTranClrgSetlmtRequest req = new SendTranClrgSetlmtRequest();
        req.setSetlAmt(new BigDecimal("1500.00"));
        assertThatThrownBy(() -> service.upsertSettlement(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tranId");
        verify(repo, never()).upsert(anyString(), any());
    }

    @Test
    void upsertSettlement_noParentRow_isLoggedAndSkipped() {
        when(repo.findByPk(eq(ClearingEventService.PARENT_ALIAS), any())).thenReturn(Optional.empty());

        service.upsertSettlement(minimalClearing());

        verify(repo, never()).upsert(eq(ClearingEventService.CLEARING_ALIAS), any());
    }

    @Test
    void aliases_matchYamlMetadata() {
        assertThat(ClearingEventService.CLEARING_ALIAS).isEqualTo("send-tran-clrg-setlmt");
        assertThat(ClearingEventService.PARENT_ALIAS).isEqualTo("send-transactions");
    }
}
