package com.poc.transactions_consumer_canonical.consumer;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalConsumer {

    private final ObjectMapper objectMapper;


    /**
     * Step 3 — Consumer receives the message from Kafka.
     * Step 4 — Maps JSON → Employee entity and saves to Oracle DB.
     */
    @KafkaListener(
            topics  = KafkaTopicConfig.TRANSACTIONS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {

        log.info("-----------------------------------------------");
        log.info("[CONSUMER]  Message received from Kafka"+message);

        try {
            Object dto = objectMapper.readValue(message, Object.class);



            log.info("[CONSUMER]  Saved to database successfully",dto);
            log.info("-----------------------------------------------");

        } catch (JsonProcessingException e) {
            log.error("[CONSUMER]  Failed to process message");
            log.info("-----------------------------------------------");
        }
    }
}
