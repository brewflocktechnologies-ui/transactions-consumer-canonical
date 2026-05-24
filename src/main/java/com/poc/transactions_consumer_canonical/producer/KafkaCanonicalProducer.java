package com.poc.transactions_consumer_canonical.producer;


import com.poc.transactions_consumer_canonical.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaCanonicalProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Step 2 — Producer sends JSON string to the Kafka topic.
     */
    public void send(String message) {
        log.info("-----------------------------------------------");
        log.info("[PRODUCER]  Sending to Kafka topic...");

        kafkaTemplate.send(KafkaTopicConfig.TRANSACTIONS_TOPIC, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[PRODUCER]  Sent successfully");
                        log.info("-----------------------------------------------");
                    } else {
                        log.error("[PRODUCER]  Failed to send");
                        log.info("-----------------------------------------------");
                    }
                });
    }
}
