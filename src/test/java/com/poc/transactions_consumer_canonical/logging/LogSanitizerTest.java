package com.poc.transactions_consumer_canonical.logging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void maskPayloadRecursivelyMasksSensitiveMapAndListValues() {
        Object masked = LogSanitizer.maskPayload(Map.of(
                "tranId", "T-1",
                "sendAcctNum", "5555444433332222",
                "nested", Map.of("token", "secret-token", "city", "Boston"),
                "items", List.of(
                        Map.of("recipEmail", "person@example.com"),
                        Map.of("status", "APPROVED")
                )
        ));

        assertThat(masked).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) masked;
        assertThat(map)
                .containsEntry("tranId", "T-1")
                .containsEntry("sendAcctNum", "************2222")
                .containsEntry("nested", Map.of("token", "***", "city", "Boston"));
        assertThat(map.get("items")).hasToString("[{recipEmail=***}, {status=APPROVED}]");
    }

    @Test
    void maskHandlesNullBlankCardLikeAndOrdinaryValues() {
        assertThat(LogSanitizer.maskPayload(null)).isNull();
        assertThat(LogSanitizer.mask(null)).isNull();
        assertThat(LogSanitizer.mask("")).isEmpty();
        assertThat(LogSanitizer.mask("4111111111111111")).isEqualTo("************1111");
        assertThat(LogSanitizer.mask("not-a-card")).isEqualTo("***");
    }
}
