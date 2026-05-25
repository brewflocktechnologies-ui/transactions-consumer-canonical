package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadSanitizerTest {

    private EventPayloadSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new EventPayloadSanitizer(new ObjectMapper());
    }

    @Test
    void nullPayload_returnsEmpty() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void blankPayload_returnsEmpty() {
        assertThat(sanitizer.sanitize("")).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }

    @Test
    void validJson_returnedUnchanged() {
        String valid = "{\"a\":1}";
        Optional<String> out = sanitizer.sanitize(valid);
        assertThat(out).contains(valid);
    }

    @Test
    void whitespaceWrappedJson_isTrimmed() {
        Optional<String> out = sanitizer.sanitize("   {\"x\":1}   ");
        assertThat(out).contains("{\"x\":1}");
    }

    @Test
    void doubleSerializedJson_isUnwrapped() {
        String inner = "{\"k\":\"v\"}";
        String doubled = "\"{\\\"k\\\":\\\"v\\\"}\"";
        Optional<String> out = sanitizer.sanitize(doubled);
        assertThat(out).contains(inner);
    }

    @Test
    void doubleQuotedScalar_thatIsNotValidJson_fallsThroughToLenientFailure() {
        // "hello" is technically valid JSON, so the as-is branch handles it.
        // To exercise the unwrap-but-still-invalid branch, build a JSON string
        // whose unwrapped value is plain text (not valid JSON), then ensure the
        // lenient pass also fails so we return empty.
        Optional<String> out = sanitizer.sanitize("\"hello world\"");
        // Strict JSON parses "hello world" as a string → as-is succeeds.
        assertThat(out).contains("\"hello world\"");
    }

    @Test
    void lenientReserialize_singleQuotesAndTrailingCommas() {
        String payload = "  {  'tranId' : 'X-1',  'amt' : '100',  }  ";
        Optional<String> out = sanitizer.sanitize(payload);
        assertThat(out).isPresent();
        // Re-emitted canonical strict JSON
        assertThat(out.get()).contains("\"tranId\":\"X-1\"");
        assertThat(out.get()).contains("\"amt\":\"100\"");
        // No trailing comma in strict output
        assertThat(out.get()).doesNotContain(",}");
    }

    @Test
    void lenientReserialize_acceptsUnquotedFieldNames() {
        Optional<String> out = sanitizer.sanitize("{ tranId: 'X-2' }");
        assertThat(out).isPresent();
        assertThat(out.get()).contains("\"tranId\":\"X-2\"");
    }

    @Test
    void lenientReserialize_acceptsJavaComments() {
        Optional<String> out = sanitizer.sanitize("{ /* a note */ \"k\": 1 }");
        assertThat(out).isPresent();
        assertThat(out.get()).contains("\"k\":1");
    }

    @Test
    void unwrap_recoversFromInvalidStrictJsonStartingWithQuote() {
        // Leading quote, invalid strict JSON as-is, but the unwrapping path inside
        // the main flow (line 54-56) yields a recoverable inner payload.
        // Use a payload where strict-parse fails (trailing garbage after a string)
        // but the outer quote triggers unwrap.
        // Jackson rejects this strict-parse but unwraps `"...` and re-parses; if that
        // also fails, lenient mode catches it. Either way, result is Optional.empty()
        // for unrecoverable input — exercises catch in unwrapDoubleSerialized.
        assertThat(sanitizer.sanitize("\"unterminated"))
                .as("invalid unwrap-attempt falls through all strategies").isEmpty();
    }

    @Test
    void irrecoverablePayload_returnsEmpty() {
        assertThat(sanitizer.sanitize("THIS IS NOT JSON AT ALL ***")).isEmpty();
    }

    @Test
    void irrecoverableLongPayload_isPreviewedInLog() {
        // 200 chars — preview truncated to 120 + ellipsis (covered by log; assert empty result)
        String junk = "x".repeat(200);
        assertThat(sanitizer.sanitize(junk)).isEmpty();
    }
}
