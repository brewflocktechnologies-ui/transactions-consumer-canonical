package com.poc.transactions_consumer_canonical;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context wires up cleanly:
 * <ul>
 *   <li>application.properties + application-test.properties parse</li>
 *   <li>@ComponentScan picks up both v1 and v2 controllers</li>
 *   <li>MetadataRegistry @PostConstruct loads & validates every YAML</li>
 *   <li>SqlBuilder, GenericTableRepository, MetadataValidator,
 *       OpenApiConfig, CorsConfig all wire up</li>
 *   <li>No circular dependencies, no duplicate bean names</li>
 * </ul>
 * Runs against H2 (Oracle-mode) with Flyway and Kafka disabled — see
 * {@code src/test/resources/application-test.properties}. Real DB
 * behaviour must be validated against Oracle XE separately.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionsConsumerCanonicalApplicationTests {

    @Test
    void contextLoads() {
        // Wiring is asserted by the act of context startup; no body required.
    }
}
