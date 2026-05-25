package com.poc.transactions_consumer_canonical.controller;

import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.producer.KafkaCanonicalProducer;
import com.poc.transactions_consumer_canonical.service.MetadataTransactionService;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerTests {

    // ── SendTransactionController (v1) ────────────────────────────────────

    @Test
    void v1_findById_delegatesAndReturnsOk() {
        SendTransactionService svc = mock(SendTransactionService.class);
        SendTransactionResponse stub = new SendTransactionResponse();
        stub.setTranId("X-1");
        when(svc.findById("X-1")).thenReturn(stub);

        SendTransactionController c = new SendTransactionController(svc);
        ResponseEntity<SendTransactionResponse> r = c.findById("X-1");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().getTranId()).isEqualTo("X-1");
        verify(svc).findById("X-1");
    }

    // ── MetadataTransactionController (v2) ────────────────────────────────

    @Test
    void v2_findById_presentReturnsOk() {
        MetadataTransactionService svc = mock(MetadataTransactionService.class);
        Map<String, Object> row = Map.of("tranId", "X-1");
        when(svc.findByPk("send-transactions", "X-1")).thenReturn(Optional.of(row));

        MetadataTransactionController c = new MetadataTransactionController(svc);
        ResponseEntity<Map<String, Object>> r = c.findById("send-transactions", "X-1");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isSameAs(row);
    }

    @Test
    void v2_findById_absentThrowsNotFound() {
        MetadataTransactionService svc = mock(MetadataTransactionService.class);
        when(svc.findByPk(any(), any())).thenReturn(Optional.empty());

        MetadataTransactionController c = new MetadataTransactionController(svc);
        assertThatThrownBy(() -> c.findById("alias", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── PublishToKafkaController ──────────────────────────────────────────

    @Test
    void publish_acceptsEnvelopeAndDelegatesToProducer() {
        KafkaCanonicalProducer producer = mock(KafkaCanonicalProducer.class);
        PublishToKafkaController c = new PublishToKafkaController(producer);

        EventEnvelope e = new EventEnvelope();
        e.setEventId("EVT-1");
        e.setEventName("PAYMENT_INITIATED");
        e.setEventSource("AIS_SERVICE");

        ResponseEntity<String> r = c.send(e);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(r.getBody()).contains("EVT-1");
        verify(producer).send(e);
    }
}
