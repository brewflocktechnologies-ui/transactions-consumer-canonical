package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.dto.SendRecipDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTranAddrDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTranDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendTransactionServiceImplTest {

    private SendTransactionRepository txnRepo;
    private SendTranDtlRepository dtlRepo;
    private SendRecipDtlRepository recipRepo;
    private SendTranAddrDtlRepository addrRepo;
    private SendTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        txnRepo = mock(SendTransactionRepository.class);
        dtlRepo = mock(SendTranDtlRepository.class);
        recipRepo = mock(SendRecipDtlRepository.class);
        addrRepo = mock(SendTranAddrDtlRepository.class);
        service = new SendTransactionServiceImpl(txnRepo, dtlRepo, recipRepo, addrRepo,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        service.setSelf(service); // route findById back through this same instance for tests
    }

    private SendTransactionRequest minimal() {
        SendTransactionRequest r = new SendTransactionRequest();
        r.setTranType("SEND");
        r.setTranCrteDt(LocalDateTime.of(2024, 11, 15, 9, 30));
        r.setCurStat("PENDING");
        r.setTranAmt(new BigDecimal("100.00"));
        return r;
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
        SendTransactionRequest r = minimal();
        SendTranDtlRequest d = new SendTranDtlRequest();
        d.setTranCrteDt(r.getTranCrteDt());
        d.setPaymtRef("PR-1");
        r.setTranDtl(d);

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X-1").build()));
        when(dtlRepo.findByTranId(anyString())).thenReturn(
                Optional.of(SendTranDtl.builder().tranId("X-1").paymtRef("PR-1").build()));

        SendTransactionResponse out = service.upsert("X-1", r);

        verify(dtlRepo).upsert(argThat(x -> "X-1".equals(x.getTranId()) && "PR-1".equals(x.getPaymtRef())));
        assertThat(out.getTranDtl()).isNotNull();
    }

    @Test
    void upsert_withRecipDtl_persistsChild() {
        SendTransactionRequest r = minimal();
        SendRecipDtlRequest rec = new SendRecipDtlRequest();
        rec.setTranCrteDt(r.getTranCrteDt());
        rec.setSendFirstNam("Alice");
        rec.setSendDob(LocalDate.of(1990, 5, 15));
        r.setRecipDtl(rec);

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X-1").build()));
        when(recipRepo.findByTranId(anyString())).thenReturn(
                Optional.of(SendRecipDtl.builder().tranId("X-1").sendFirstNam("Alice").build()));

        service.upsert("X-1", r);

        verify(recipRepo).upsert(argThat(x -> "Alice".equals(x.getSendFirstNam())));
    }

    @Test
    void upsert_withAddrDtlEmpty_clearsAll() {
        SendTransactionRequest r = minimal();
        r.setAddrDtl(List.of());
        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", r);

        verify(addrRepo).deleteByTranId("X");
        verify(addrRepo, never()).mergeAll(anyList());
    }

    @Test
    void upsert_withAddrDtlPopulated_mergesAndPrunes() {
        SendTransactionRequest r = minimal();
        SendTranAddrDtlRequest sender = new SendTranAddrDtlRequest();
        sender.setId("ADDR-1"); // explicit id
        sender.setAddrType("SENDER");
        SendTranAddrDtlRequest recipient = new SendTranAddrDtlRequest();
        recipient.setAddrType("RECIPIENT"); // no id → UUID generated
        r.setAddrDtl(List.of(sender, recipient));

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", r);

        ArgumentCaptor<List<SendTranAddrDtl>> merged = ArgumentCaptor.captor();
        verify(addrRepo).mergeAll(merged.capture());
        assertThat(merged.getValue()).hasSize(2);
        assertThat(merged.getValue().get(0).getId()).isEqualTo("ADDR-1");
        assertThat(merged.getValue().get(1).getId()).isNotNull().isNotEmpty();

        ArgumentCaptor<List<String>> keepIds = ArgumentCaptor.captor();
        verify(addrRepo).deleteByTranIdNotIn(eq("X"), keepIds.capture());
        assertThat(keepIds.getValue()).contains("ADDR-1");
    }

    @Test
    void upsert_withAddrIdBlank_generatesUuid() {
        SendTransactionRequest r = minimal();
        SendTranAddrDtlRequest a = new SendTranAddrDtlRequest();
        a.setId("   ");
        a.setAddrType("SENDER");
        r.setAddrDtl(List.of(a));

        when(txnRepo.findById(anyString())).thenReturn(
                Optional.of(SendTransaction.builder().tranId("X").build()));

        service.upsert("X", r);

        ArgumentCaptor<List<SendTranAddrDtl>> merged = ArgumentCaptor.captor();
        verify(addrRepo).mergeAll(merged.capture());
        assertThat(merged.getValue().get(0).getId()).isNotBlank().isNotEqualTo("   ");
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
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
