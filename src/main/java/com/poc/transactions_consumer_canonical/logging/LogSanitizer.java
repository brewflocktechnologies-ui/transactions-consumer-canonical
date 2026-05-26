package com.poc.transactions_consumer_canonical.logging;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Helpers for masking sensitive payload values before they hit application logs.
 * <p>
 * The set of "sensitive" JSON field names below is intentionally broad — better
 * to over-mask in logs than to leak PCI/PII data. Audit timestamps and DB
 * column names that include the word "ID" (tranId, etc.) are not masked.
 * <p>
 * <strong>This utility is opt-in.</strong> Service/repo code should call
 * {@link #maskPayload(java.util.Map)} before logging a request body. Bound JDBC
 * parameter logging (Spring's {@code org.springframework.jdbc.core} DEBUG) is
 * the larger leak vector — that is silenced in {@code application-prod.properties}.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /** JSON field names whose values should be masked in logs. */
    public static final Set<String> SENSITIVE_FIELDS = Set.of(
            "sendAcctNum", "recipAcctNum",
            "sendCardNum", "recipCardNum",
            "sendCardExpirDt", "recipCardExpirDt",
            "sendGovtIdUri", "recipGovtIdUri",
            "sendDob", "recipDob",
            "sendPhn", "recipPhn",
            "sendEmail", "recipEmail",
            "acctNum",
            "bncGtwyRqst", "bncGtwyResp",
            "origRqstPyld", "origRespPyld",
            "password", "secret", "token", "apiKey", "authorization"
    );

    private static final Pattern PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");

    /**
     * Returns a copy of the payload with sensitive values replaced by {@code "***"}.
     * Walks nested Maps and Lists recursively.
     */
    @SuppressWarnings("unchecked")
    public static Object maskPayload(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = java.util.LinkedHashMap.newLinkedHashMap(map.size());
            for (var e : map.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (SENSITIVE_FIELDS.contains(k)) {
                    out.put(k, mask(v));
                } else {
                    out.put(k, maskPayload(v));
                }
            }
            return out;
        }
        if (value instanceof java.util.List<?> list) {
            java.util.List<Object> out = new java.util.ArrayList<>(list.size());
            for (Object item : list) out.add(maskPayload(item));
            return out;
        }
        return value;
    }

    /** Masks a single scalar value — keeps last 4 chars of long numeric strings (card-like). */
    public static String mask(Object v) {
        if (v == null) return null;
        String s = v.toString();
        if (s.isEmpty()) return s;
        if (PAN_PATTERN.matcher(s).matches()) {
            return "*".repeat(s.length() - 4) + s.substring(s.length() - 4);
        }
        return "***";
    }
}
