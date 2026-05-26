package com.poc.transactions_consumer_canonical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestIdFilter#sanitize(String)}.
 * No Spring context needed — the method is package-visible and pure.
 */
class RequestIdFilterTest {

    // ── Null / blank → generate UUID ─────────────────────────────────────────

    @Test
    void null_input_generatesUuid() {
        String result = RequestIdFilter.sanitize(null);
        assertThat(result).isNotBlank().matches("[a-f0-9\\-]{36}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void blank_input_generatesUuid(String blank) {
        String result = RequestIdFilter.sanitize(blank);
        assertThat(result).isNotBlank().matches("[a-f0-9\\-]{36}");
    }

    // ── Valid inputs pass through unchanged ───────────────────────────────────

    @Test
    void standard_uuid_passesThrough() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(RequestIdFilter.sanitize(uuid)).isEqualTo(uuid);
    }

    @Test
    void alphanumeric_with_safe_separators_passesThrough() {
        String id = "req_ABC-123.v2";
        assertThat(RequestIdFilter.sanitize(id)).isEqualTo(id);
    }

    // ── Unsafe characters are stripped ───────────────────────────────────────

    @Test
    void crlf_injection_stripped() {
        // Classic log-injection attempt: CRLF and spaces are stripped
        String malicious = "abc\r\nX-Injected-Header: evil";
        String result = RequestIdFilter.sanitize(malicious);
        // CRLF, spaces, colons removed — only safe chars survive
        assertThat(result)
                .doesNotContain("\r", "\n", " ", ":")
                .isEqualTo("abcX-Injected-Headerevil");
    }

    @Test
    void newline_only_stripped() {
        assertThat(RequestIdFilter.sanitize("id\nvalue")).isEqualTo("idvalue");
    }

    @Test
    void special_chars_stripped_leaving_safe_chars() {
        assertThat(RequestIdFilter.sanitize("abc<script>alert(1)</script>"))
                .isEqualTo("abcscriptalert1script");
    }

    @Test
    void only_unsafe_chars_generates_uuid() {
        // After stripping, nothing remains → fall back to UUID
        String result = RequestIdFilter.sanitize("!@#$%^&*()");
        assertThat(result).isNotBlank().matches("[a-f0-9\\-]{36}");
    }

    // ── Length truncation ─────────────────────────────────────────────────────

    @Test
    void oversized_input_truncated_to_max_length() {
        String long64  = "a".repeat(RequestIdFilter.MAX_ID_LENGTH);
        String long128 = "a".repeat(RequestIdFilter.MAX_ID_LENGTH * 2);

        assertThat(RequestIdFilter.sanitize(long64)).hasSize(RequestIdFilter.MAX_ID_LENGTH);
        assertThat(RequestIdFilter.sanitize(long128)).hasSize(RequestIdFilter.MAX_ID_LENGTH);
    }

    @Test
    void exactly_max_length_passes_through() {
        String exact = "x".repeat(RequestIdFilter.MAX_ID_LENGTH);
        assertThat(RequestIdFilter.sanitize(exact)).isEqualTo(exact);
    }

    @Test
    void one_under_max_length_passes_through() {
        String underMax = "x".repeat(RequestIdFilter.MAX_ID_LENGTH - 1);
        assertThat(RequestIdFilter.sanitize(underMax)).isEqualTo(underMax);
    }

    // ── Truncation happens before stripping ───────────────────────────────────

    @Test
    void truncation_applied_before_char_stripping() {
        // 64 safe chars followed by unsafe chars — only the first 64 should survive
        String safe    = "a".repeat(RequestIdFilter.MAX_ID_LENGTH);
        String unsafe  = "!".repeat(RequestIdFilter.MAX_ID_LENGTH);
        String combined = safe + unsafe;

        String result = RequestIdFilter.sanitize(combined);
        assertThat(result).isEqualTo(safe);
    }
}
