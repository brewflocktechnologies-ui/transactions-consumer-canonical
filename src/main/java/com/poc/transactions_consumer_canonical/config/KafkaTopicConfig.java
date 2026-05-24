package com.poc.transactions_consumer_canonical.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic and broker configuration.
 *
 * <p>{@link EnableKafka} activates the {@code @KafkaListener} scanning.
 * The {@link NewTopic} bean is picked up by Spring's {@code KafkaAdmin}
 * which automatically creates the topic on startup if it does not yet exist.
 */
@Configuration
@EnableKafka
public class KafkaTopicConfig {

    /** Topic name shared across producer and consumer. */
    public static final String TRANSACTIONS_TOPIC = "transactions-group";

    @Bean
    public NewTopic employeeTopic() {
        return TopicBuilder.name(TRANSACTIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
