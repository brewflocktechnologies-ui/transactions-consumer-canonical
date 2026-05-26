package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import com.poc.transactions_consumer_canonical.repository.SendRecipDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranAddrDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendTransactionServiceImplTest {

    private static final String ADDR_ID          = "ADDR-1";
    private static final String PATH_TRAN_ID     = "FROM-PATH";
    private static final String KEY_TRAN_CRTE_DT = "tranCrteDt";
    private static final String KEY_ADDR_TYPE    = "addrType";

    private SendTransactionRepository txnRepo;
    private SendTranDtlRepository dtlRepo;
    private SendRecipDtlRepository recipRepo;
    private SendTranAddrDtlRepository addrRepo;
    private GenericTableRepository genericRepo;
    private SendTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        txnRepo = mock(SendTransactionRepository.class);
        dtlRepo = mock(SendTranDtlRepository.class);
        recipRepo = mock(SendRecipDtlRepository.class);
        addrRepo = mock(SendTranAddrDtlRepository.class);
        genericRepo = mock(GenericTableRepository.class);
        when(genericRepo.findByPk(eq("send-tran-clrg-setlmt"), anyString())).thenReturn(Optional.empty());
        service = new SendTransactionServiceImpl(txnRepo, dtlRepo, recipRepo, addrRepo, genericRepo,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        service.setSelf(service); // route findById back through this same instance for tests
    }

    private Map<String, Object> minimal() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tranType", "SEND");
        m.put(KEY_TRAN_CRTE_DT, LocalDateTime.of(2024, 11, 15, 9, 30));
        m.put("curStat", "PENDING");
        m.put("tranAmt", new BigDecimal("100.00"));
        return m;
    }

    @Test
    void upsert_nullCanonical_throws() {
        assertThatThrownBy(() -> service.upsert("X-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical");
        verify(txnRepo, never()).upsert(any());
    }

    @Test
    void upsert_parentOnly_persistsOnlyParent() {
        when(txnRepo.findById("X-1")).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X-1").build()));
        when(dtlRepo.findByTranId("X-1")).thenReturn(Optional.empty());
        when(recipRepo.findByTranId("X-1")).thenReturn(Optional.empty());
        when(addrRepo.findByTranId("X-1")).thenReturn(List.of());

        SendTransactionResponse out = service.upsert("X-1", minimal());

        verify(txnRepo).upsert(argThat(t -> "X-1".equals(t.getTranId())));
        verify(dtlRepo, never()).upsert(any());
        verify(recipRepo, never()).upsert(any());
        verify(addrRepo, never()).mergeAll(anyList());
        assertThat(out.getTranId()).isEqualTo("X-1");
    }

    @Test
    void upsert_withTranDtl_persistsChild() {
        Map<String, Object> canonical = minimal();
        Map<String, Object> dtl = new LinkedHashMap<>();
        dtl.put(KEY_TRAN_CRTE_DT, canonical.get(KEY_TRAN_CRTE_DT));
        dtl.put("paymtRef", "PR-1");
        canonical.put(CanonicalMappingEngine.SECTION_TRAN_DTL, dtl);

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X-1").build()));
        when(dtlRepo.findByTranId(anyString())).thenReturn(
                Optional.of(SendTranDtl.builder().tranId("X-1").paymtRef("PR-1").build()));

        SendTransactionResponse out = service.upsert("X-1", canonical);

        verify(dtlRepo).upsert(argThat(x -> "X-1".equals(x.getTranId()) && "PR-1".equals(x.getPaymtRef())));
        assertThat(out.getTranDtl()).isNotNull();
    }

    @Test
    void upsert_withRecipDtl_persistsChild() {
        Map<String, Object> canonical = minimal();
        Map<String, Object> recip = new LinkedHashMap<>();
        recip.put(KEY_TRAN_CRTE_DT, canonical.get(KEY_TRAN_CRTE_DT));
        recip.put("sendFirstNam", "Alice");
        recip.put("sendDob", LocalDate.of(1990, 5, 15));
        canonical.put(CanonicalMappingEngine.SECTION_RECIP_DTL, recip);

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X-1").build()));
        when(recipRepo.findByTranId(anyString())).thenReturn(
                Optional.of(SendRecipDtl.builder().tranId("X-1").sendFirstNam("Alice").build()));

        service.upsert("X-1", canonical);

        verify(recipRepo).upsert(argThat(x -> "Alice".equals(x.getSendFirstNam())));
    }

    @Test
    void upsert_withAddrDtlEmpty_clearsAll() {
        Map<String, Object> canonical = minimal();
        canonical.put(CanonicalMappingEngine.SECTION_ADDR_DTL, new ArrayList<>());
        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", canonical);

        verify(addrRepo).deleteByTranId("X");
        verify(addrRepo, never()).mergeAll(anyList());
    }

    @Test
    void upsert_withAddrDtlPopulated_mergesAndPrunes() {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("id", ADDR_ID);
        sender.put(KEY_ADDR_TYPE, "SENDER");
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put(KEY_ADDR_TYPE, "RECIPIENT"); // no id → UUID generated

        Map<String, Object> canonical = minimal();
        canonical.put(CanonicalMappingEngine.SECTION_ADDR_DTL, List.of(sender, recipient));

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", canonical);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SendTranAddrDtl>> merged = ArgumentCaptor.forClass(List.class);
        verify(addrRepo).mergeAll(merged.capture());
        assertThat(merged.getValue()).hasSize(2);
        assertThat(merged.getValue().get(0).getId()).isEqualTo(ADDR_ID);
        assertThat(merged.getValue().get(1).getId()).isNotNull().isNotEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keepIds = ArgumentCaptor.forClass(List.class);
        verify(addrRepo).deleteByTranIdNotIn(eq("X"), keepIds.capture());
        assertThat(keepIds.getValue()).contains(ADDR_ID);
    }

    @Test
    void upsert_withAddrIdBlank_generatesUuid() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", "   ");
        a.put(KEY_ADDR_TYPE, "SENDER");

        Map<String, Object> canonical = minimal();
        canonical.put(CanonicalMappingEngine.SECTION_ADDR_DTL, List.of(a));

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", canonical);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SendTranAddrDtl>> merged = ArgumentCaptor.forClass(List.class);
        verify(addrRepo).mergeAll(merged.capture());
        assertThat(merged.getValue().get(0).getId()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void upsert_forcesPathDerivedTranIdOverAnythingInThePayload() {
        Map<String, Object> canonical = minimal();
        canonical.put("tranId", "FROM-PAYLOAD"); // engine never sets this, but be defensive

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId(PATH_TRAN_ID).build()));

        service.upsert(PATH_TRAN_ID, canonical);

        verify(txnRepo).upsert(argThat(t -> PATH_TRAN_ID.equals(t.getTranId())));
    }

    @Test
    void upsert_stripsChildSectionsBeforeBindingParent() {
        // tranDtl key must be stripped before Jackson binds the parent model
        Map<String, Object> canonical = minimal();
        canonical.put(CanonicalMappingEngine.SECTION_TRAN_DTL, new HashMap<>(Map.of("paymtRef", "PR-X")));
        canonical.put(CanonicalMappingEngine.SECTION_RECIP_DTL, new HashMap<>(Map.of("sendFirstNam", "A")));
        canonical.put(CanonicalMappingEngine.SECTION_ADDR_DTL, List.of());

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", canonical);

        // No reflective field on SendTransaction matches these keys — succeeds only if stripped.
        verify(txnRepo).upsert(argThat(t -> "X".equals(t.getTranId())));
    }

    @Test
    void findById_missing_throws() {
        when(txnRepo.findById("M")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById("M"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void findById_present_returnsFullGraph() {
        when(txnRepo.findById("X")).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").tranType("SEND").build()));
        when(dtlRepo.findByTranId("X")).thenReturn(
                Optional.of(SendTranDtl.builder().tranId("X").paymtRef("PR-1").build()));
        when(recipRepo.findByTranId("X")).thenReturn(
                Optional.of(SendRecipDtl.builder().tranId("X").sendFirstNam("A").build()));
        when(addrRepo.findByTranId("X")).thenReturn(List.of(
                SendTranAddrDtl.builder().id("A-1").tranId("X").addrType("SENDER").build()));

        SendTransactionResponse out = service.findById("X");

        assertThat(out.getTranId()).isEqualTo("X");
        assertThat(out.getTranDtl()).isNotNull();
        assertThat(out.getRecipDtl().getSendFirstNam()).isEqualTo("A");
        assertThat(out.getAddrDtl()).hasSize(1);
        // No SEND_TRAN_CLRG_SETLMT row → clearing & settlement remain null
        assertThat(out.getClearing()).isNull();
        assertThat(out.getSettlement()).isNull();
    }

    @Test
    void findById_includesClearingAndSettlement_whenClrgSetlmtRowExists() {
        when(txnRepo.findById("X")).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").tranType("SEND").build()));
        when(dtlRepo.findByTranId("X")).thenReturn(Optional.empty());
        when(recipRepo.findByTranId("X")).thenReturn(Optional.empty());
        when(addrRepo.findByTranId("X")).thenReturn(List.of());

        Map<String, Object> clrgSetlmtRow = new HashMap<>();
        clrgSetlmtRow.put("tranId", "X");
        clrgSetlmtRow.put("clrgSt", "SETTLED");
        clrgSetlmtRow.put("clrgDtTs", LocalDateTime.of(2026, 5, 26, 10, 0));
        clrgSetlmtRow.put("acqIcaRefTxt", "ACQ-REF-1");
        clrgSetlmtRow.put("setlDt", LocalDate.of(2026, 5, 26));
        clrgSetlmtRow.put("setlAmt", new BigDecimal("1500.00"));
        clrgSetlmtRow.put("setlCurrCd", "USD");
        clrgSetlmtRow.put("tranFileId", "FILE-20260526-001");
        when(genericRepo.findByPk("send-tran-clrg-setlmt", "X")).thenReturn(Optional.of(clrgSetlmtRow));

        SendTransactionResponse out = service.findById("X");

        assertThat(out.getClearing()).isNotNull();
        assertThat(out.getClearing().getClrgSt()).isEqualTo("SETTLED");
        assertThat(out.getClearing().getAcqIcaRefTxt()).isEqualTo("ACQ-REF-1");
        assertThat(out.getClearing().getClrgDtTs()).isEqualTo(LocalDateTime.of(2026, 5, 26, 10, 0));

        assertThat(out.getSettlement()).isNotNull();
        assertThat(out.getSettlement().getSetlAmt()).isEqualByComparingTo("1500.00");
        assertThat(out.getSettlement().getSetlCurrCd()).isEqualTo("USD");
        assertThat(out.getSettlement().getTranFileId()).isEqualTo("FILE-20260526-001");
        assertThat(out.getSettlement().getSetlDt()).isEqualTo(LocalDate.of(2026, 5, 26));
    }

}
