package com.poc.transactions_consumer_canonical.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaCanonicalProducerTest {

    private EventEnvelope envelope() {
        EventEnvelope e = new EventEnvelope();
        e.setEventId("EVT-1");
        e.setEventName("PAYMENT");
        e.setCorrelationId("CORR-1");
        return e;
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_serializesAndDispatches() {
        KafkaTemplate<String, String> tpl = mock(KafkaTemplate.class);
        SendResult<String, String> result = mock(SendResult.class);
        RecordMetadata meta = new RecordMetadata(new TopicPartition("transactions-group", 0), 0L, 1, 0L, 0, 0);
        when(result.getRecordMetadata()).thenReturn(meta);
        when(tpl.send(eq(KafkaTopicConfig.TRANSACTIONS_TOPIC), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        KafkaCanonicalProducer p = new KafkaCanonicalProducer(tpl, new ObjectMapper());
        p.send(envelope());

        verify(tpl).send(eq(KafkaTopicConfig.TRANSACTIONS_TOPIC), eq("CORR-1"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_kafkaFailureIsLoggedNotRethrown() {
        KafkaTemplate<String, String> tpl = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(tpl.send(anyString(), anyString(), anyString())).thenReturn(failed);

        KafkaCanonicalProducer p = new KafkaCanonicalProducer(tpl, new ObjectMapper());
        // Should not throw — exception is logged in the .whenComplete handler
        p.send(envelope());
        verify(tpl).send(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_serializationFailure_doesNotSendToKafka() throws Exception {
        KafkaTemplate<String, String> tpl = mock(KafkaTemplate.class);
        ObjectMapper bad = mock(ObjectMapper.class);
        when(bad.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("boom") {});

        KafkaCanonicalProducer p = new KafkaCanonicalProducer(tpl, bad);
        p.send(envelope());

        verify(tpl, never()).send(anyString(), anyString(), anyString());
    }
}
