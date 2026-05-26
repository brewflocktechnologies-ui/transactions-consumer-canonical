package com.poc.transactions_consumer_canonical;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request-id / correlation filter that sets an MDC key ({@code traceId}) for logs
 * and echoes the {@code X-Request-ID} response header.
 *
 * <p>Uses a client-supplied {@code X-Request-ID} when present, otherwise generates
 * a UUID. The client-supplied value is sanitized before use:
 * <ul>
 *   <li>Stripped to at most {@value #MAX_ID_LENGTH} characters to prevent log injection
 *       via oversized headers.</li>
 *   <li>Non-safe characters (anything outside {@code [a-zA-Z0-9\-_.]}) are removed.
 *       This covers the common UUID / correlation-id character set while blocking
 *       newlines, CRLF sequences, and other log-injection vectors.</li>
 *   <li>If the sanitized value is blank (e.g. the header contained only special
 *       characters), a fresh UUID is generated instead.</li>
 * </ul>
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_TRACE_ID      = "traceId";

    /** Maximum length accepted from a client-supplied request ID. */
    static final int MAX_ID_LENGTH = 64;

    /** Characters allowed in a sanitized request ID. */
    private static final Pattern SAFE_CHARS = Pattern.compile("[^a-zA-Z0-9\\-_.]");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(MDC_TRACE_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /**
     * Sanitizes a raw {@code X-Request-ID} header value.
     *
     * @param raw the header value, may be {@code null}
     * @return a safe, non-blank request ID (falls back to a generated UUID)
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID().toString();
        }
        // Truncate first to avoid regex work on arbitrarily long strings
        String truncated = raw.length() > MAX_ID_LENGTH ? raw.substring(0, MAX_ID_LENGTH) : raw;
        String cleaned   = SAFE_CHARS.matcher(truncated).replaceAll("");
        return cleaned.isBlank() ? UUID.randomUUID().toString() : cleaned;
    }
}
