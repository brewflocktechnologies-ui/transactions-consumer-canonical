package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingRegistry;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalRuleEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventPayloadSanitizer;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping;
import com.poc.transactions_consumer_canonical.dto.SendTranClrgSetlmtRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import com.poc.transactions_consumer_canonical.service.ClearingEventService;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaCanonicalConsumerTest {

    private ObjectMapper mapper;
    private CanonicalMappingRegistry registry;
    private CanonicalRuleEngine ruleEngine;
    private EventPayloadSanitizer sanitizer;
    private CanonicalMappingEngine mappingEngine;
    private SendTransactionService service;
    private ClearingEventService clearingService;
    private KafkaCanonicalConsumer consumer;

    @BeforeEach
    void setUp() {
        mapper = mock(ObjectMapper.class);
        registry = mock(CanonicalMappingRegistry.class);
        ruleEngine = mock(CanonicalRuleEngine.class);
        sanitizer = mock(EventPayloadSanitizer.class);
        mappingEngine = mock(CanonicalMappingEngine.class);
        service = mock(SendTransactionService.class);
        clearingService = mock(ClearingEventService.class);
        consumer = new KafkaCanonicalConsumer(
                mapper, registry, ruleEngine, sanitizer, mappingEngine, service, clearingService);
    }

    private EventEnvelope envelope() {
        EventEnvelope e = new EventEnvelope();
        e.setEventId("EVT-1");
        e.setEventName("PAYMENT_INITIATED");
        e.setEventSource("AIS_SERVICE");
        e.setCorrelationId("CORR-1");
        e.setEventTimestamp(1L);
        e.setEventMetadata("{}");
        e.setEventPayload("{\"tranId\":\"X-1\"}");
        return e;
    }

    @Test
    void envelopeDeserializationFailure_isLoggedAndSkipped() throws Exception {
        when(mapper.readValue("not-json", EventEnvelope.class))
                .thenThrow(new JsonProcessingException("bad") {});
        consumer.consume("not-json");
        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void ignoreFlag_skipsProcessing() throws Exception {
        EventEnvelope e = envelope();
        e.setIgnore(true);
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(e);

        consumer.consume("{}");

        verify(registry, never()).findByEventName(anyString());
        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void unknownEventName_skipsProcessing() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        when(registry.findByEventName(anyString())).thenReturn(Optional.empty());

        consumer.consume("{}");

        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void ruleEngineBlocks_skipsProcessing() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("PAYMENT");
        when(registry.findByEventName(anyString())).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(false);

        consumer.consume("{}");

        verify(sanitizer, never()).sanitize(anyString());
        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void sanitizerEmpty_skipsProcessing() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("PAYMENT");
        when(registry.findByEventName(anyString())).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.empty());

        consumer.consume("{}");

        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void payloadDeserializationFailure_isLoggedAndSkipped() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("PAYMENT");
        when(registry.findByEventName(anyString())).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.of("{}"));
        when(mapper.readValue("{}", TransactionEventAxonMessage.class))
                .thenThrow(new JsonProcessingException("payload bad") {});

        consumer.consume("{}");

        verify(service, never()).upsert(anyString(), any());
    }

    @Test
    void happyPath_callsServiceUpsert() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("PAYMENT");
        when(registry.findByEventName(anyString())).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.of("{}"));
        when(mapper.readValue("{}", TransactionEventAxonMessage.class))
                .thenReturn(new TransactionEventAxonMessage());
        when(mappingEngine.extractTranId(any(), any(), any())).thenReturn("X-1");
        SendTransactionRequest req = new SendTransactionRequest();
        req.setTranType("SEND");
        when(mappingEngine.map(any(), any(), any())).thenReturn(req);

        consumer.consume("{}");

        verify(service).upsert("X-1", req);
    }

    private void stubClrgSetlmtMappingPipeline(String eventName, String eventType) throws Exception {
        EventEnvelope env = envelope();
        env.setEventName(eventName);
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(env);

        EventTypeMapping m = new EventTypeMapping();
        m.setEventType(eventType);
        when(registry.findByEventName(eventName)).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.of(env.getEventPayload()));
        when(mapper.readValue(env.getEventPayload(), TransactionEventAxonMessage.class))
                .thenReturn(new TransactionEventAxonMessage());
        when(mappingEngine.extractTranId(any(), any(), any())).thenReturn("X-1");
        // applyTo returns the target as-is for assertion plumbing
        when(mappingEngine.applyTo(any(), any(), any(SendTranClrgSetlmtRequest.class)))
                .thenAnswer(inv -> inv.getArgument(2));
    }

    @Test
    void clearingEvent_routesToClearingService_typedRequest() throws Exception {
        stubClrgSetlmtMappingPipeline("CLEARING_SETTLED", "CLEARING");

        consumer.consume("{}");

        ArgumentCaptor<SendTranClrgSetlmtRequest> captor =
                ArgumentCaptor.forClass(SendTranClrgSetlmtRequest.class);
        verify(clearingService).upsertClearing(captor.capture());
        assertThat(captor.getValue().getTranId()).isEqualTo("X-1");
        verify(clearingService, never()).upsertSettlement(any());
        verify(service, never()).upsert(anyString(), any());
        verify(mappingEngine, never()).map(any(), any(), any());
    }

    @Test
    void settlementEvent_routesToSettlementService_typedRequest() throws Exception {
        stubClrgSetlmtMappingPipeline("SETTLEMENT", "SETTLEMENT");

        consumer.consume("{}");

        ArgumentCaptor<SendTranClrgSetlmtRequest> captor =
                ArgumentCaptor.forClass(SendTranClrgSetlmtRequest.class);
        verify(clearingService).upsertSettlement(captor.capture());
        assertThat(captor.getValue().getTranId()).isEqualTo("X-1");
        verify(clearingService, never()).upsertClearing(any());
        verify(service, never()).upsert(anyString(), any());
        verify(mappingEngine, never()).map(any(), any(), any());
    }

    @Test
    void clrgSetlmtEvent_payloadDeserialiseFailure_isLoggedAndSkipped() throws Exception {
        EventEnvelope env = envelope();
        env.setEventName("CLEARING_SETTLED");
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(env);

        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("CLEARING");
        when(registry.findByEventName("CLEARING_SETTLED")).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.of("{bad json}"));
        when(mapper.readValue("{bad json}", TransactionEventAxonMessage.class))
                .thenThrow(new JsonProcessingException("bad payload") {});

        consumer.consume("{}");

        verify(clearingService, never()).upsertClearing(any());
        verify(clearingService, never()).upsertSettlement(any());
    }

    @Test
    void clearingEvent_serviceFailure_isLoggedNotRethrown() throws Exception {
        stubClrgSetlmtMappingPipeline("CLEARING_SETTLED", "CLEARING");
        doThrow(new RuntimeException("db error")).when(clearingService).upsertClearing(any());

        consumer.consume("{}");
        // no rethrow ⇒ test passes
    }

    @Test
    void servicePersistError_isLoggedNotRethrown() throws Exception {
        when(mapper.readValue("{}", EventEnvelope.class)).thenReturn(envelope());
        EventTypeMapping m = new EventTypeMapping();
        m.setEventType("PAYMENT");
        when(registry.findByEventName(anyString())).thenReturn(Optional.of(m));
        when(ruleEngine.shouldProcess(any(), any())).thenReturn(true);
        when(sanitizer.sanitize(anyString())).thenReturn(Optional.of("{}"));
        when(mapper.readValue("{}", TransactionEventAxonMessage.class))
                .thenReturn(new TransactionEventAxonMessage());
        when(mappingEngine.extractTranId(any(), any(), any())).thenReturn("X-1");
        when(mappingEngine.map(any(), any(), any())).thenReturn(new SendTransactionRequest());
        when(service.upsert(anyString(), any())).thenThrow(new RuntimeException("db error"));

        consumer.consume("{}");
        // no rethrow ⇒ test passes
    }
}
