package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.poc.transactions_consumer_canonical.dto.SendRecipDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTranAddrDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTranDtlRequest;
import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies an {@link EventTypeMapping} to a {@link TransactionEventAxonMessage}
 * and produces a fully-populated {@link SendTransactionRequest} (the canonical object).
 *
 * <h3>Field mapping</h3>
 * For each {@link FieldMapping}:
 * <ol>
 *   <li>Read the value via the named getter on {@code TransactionEventAxonMessage}.</li>
 *   <li>Auto-coerce it to the setter's declared parameter type
 *       (String→BigDecimal, String→LocalDate, String→LocalDateTime, String→Long …).</li>
 *   <li>Write the coerced value via the named setter on the target DTO.</li>
 * </ol>
 * Null or blank source values are silently skipped.
 * Unresolvable getters/setters are logged at DEBUG and skipped.
 *
 * <h3>Fixed fields (always set)</h3>
 * <ul>
 *   <li>{@code tranType}   — literal from YAML</li>
 *   <li>{@code tranCrteDt} — derived from {@code EventEnvelope.eventTimestamp} (epoch-millis → UTC)</li>
 *   <li>{@code crteUserNam} / {@code updtUserNam} — "SYSTEM"</li>
 *   <li>{@code tranDtl.eventId}, {@code eventTs}, {@code eventCorltnId} — envelope metadata</li>
 * </ul>
 */
@Slf4j
@Component
public class CanonicalMappingEngine {

    private static final String SYSTEM_USER = "SYSTEM";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Derives the DB {@code TRAN_ID} from the YAML {@code tranIdSource} field,
     * falling back to {@code EventEnvelope.correlationId}.
     */
    public String extractTranId(EventTypeMapping mapping,
                                TransactionEventAxonMessage txn,
                                EventEnvelope envelope) {
        String source = mapping.getTranIdSource();
        if (source != null && !source.isBlank()) {
            Object val = safeRead(txn, source);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        log.debug("[ENGINE] tranIdSource '{}' yielded null — falling back to correlationId", source);
        return envelope.getCorrelationId();
    }

    /**
     * Applies the full {@link EventTypeMapping} and returns a populated
     * {@link SendTransactionRequest} ready to be passed to
     * {@code SendTransactionService.upsert()}.
     */
    public SendTransactionRequest map(EventTypeMapping mapping,
                                     TransactionEventAxonMessage txn,
                                     EventEnvelope envelope) {

        LocalDateTime eventDt = epochToUtc(envelope.getEventTimestamp());

        // ── Parent ────────────────────────────────────────────────────────────
        SendTransactionRequest req = new SendTransactionRequest();
        req.setTranType(mapping.getTranType());
        req.setTranCrteDt(eventDt);
        req.setCrteUserNam(SYSTEM_USER);
        req.setUpdtUserNam(SYSTEM_USER);
        req.setNonFinTxn(false); // DB column is NOT NULL; default to 0 (financial txn) unless overridden by YAML mapping
        applyMappings(mapping.getTransaction(), txn, req);

        // ── SendTranDtlRequest (1:1 child) ────────────────────────────────────
        if (hasEntries(mapping.getTranDtl())) {
            SendTranDtlRequest dtl = new SendTranDtlRequest();
            dtl.setTranCrteDt(eventDt);
            dtl.setEventId(envelope.getEventId());
            dtl.setEventTs(eventDt);
            dtl.setEventCorltnId(envelope.getCorrelationId());
            dtl.setCrteUserNam(SYSTEM_USER);
            dtl.setUpdtUserNam(SYSTEM_USER);
            applyMappings(mapping.getTranDtl(), txn, dtl);
            req.setTranDtl(dtl);
        }

        // ── SendRecipDtlRequest (1:1 child) ───────────────────────────────────
        if (hasEntries(mapping.getRecipDtl())) {
            SendRecipDtlRequest recip = new SendRecipDtlRequest();
            recip.setTranCrteDt(eventDt);
            recip.setCrteUserNam(SYSTEM_USER);
            recip.setUpdtUserNam(SYSTEM_USER);
            applyMappings(mapping.getRecipDtl(), txn, recip);
            req.setRecipDtl(recip);
        }

        // ── SendTranAddrDtlRequest (1:many child) ─────────────────────────────
        if (hasEntries(mapping.getAddrDtl())) {
            List<SendTranAddrDtlRequest> addrs = new ArrayList<>();
            for (AddrDtlGroup group : mapping.getAddrDtl()) {
                SendTranAddrDtlRequest addr = new SendTranAddrDtlRequest();
                addr.setAddrType(group.getAddrType());
                addr.setCrteUserNam(SYSTEM_USER);
                addr.setUpdtUserNam(SYSTEM_USER);
                int mapped = applyMappings(group.getMappings(), txn, addr);
                // Only include an address entry if at least one content field was populated
                if (mapped > 0) {
                    addrs.add(addr);
                }
            }
            req.setAddrDtl(addrs.isEmpty() ? null : addrs);
        }

        // ── Source-specific overlay ────────────────────────────────────────────
        SourceMapping sm = resolveSourceMapping(mapping.getSourceMappings(), envelope.getEventSource());
        if (sm != null) {
            applySourceOverlay(sm, txn, req, eventDt, envelope);
        }

        return req;
    }

    /**
     * Looks up a {@link SourceMapping} by event source using case-insensitive matching.
     * Returns {@code null} when the map is absent or no entry matches.
     */
    private SourceMapping resolveSourceMapping(Map<String, SourceMapping> sourceMappings, String eventSource) {
        if (sourceMappings == null || eventSource == null || eventSource.isBlank()) return null;
        return sourceMappings.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(eventSource.trim()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Applies source-specific mappings on top of already-populated DTOs.
     * Missing source fields in the JSON are silently skipped (target retains its value).
     */
    private void applySourceOverlay(SourceMapping sm,
                                    TransactionEventAxonMessage txn,
                                    SendTransactionRequest req,
                                    LocalDateTime eventDt,
                                    EventEnvelope envelope) {
        if (hasEntries(sm.getTransaction())) applyMappings(sm.getTransaction(), txn, req);
        if (hasEntries(sm.getTranDtl()))     overlayTranDtl(sm, txn, req, eventDt, envelope);
        if (hasEntries(sm.getRecipDtl()))    overlayRecipDtl(sm, txn, req, eventDt);
        if (hasEntries(sm.getAddrDtl()))     overlayAddrDtl(sm, txn, req);
    }

    private void overlayTranDtl(SourceMapping sm,
                                TransactionEventAxonMessage txn,
                                SendTransactionRequest req,
                                LocalDateTime eventDt,
                                EventEnvelope envelope) {
        SendTranDtlRequest dtl = req.getTranDtl();
        if (dtl == null) {
            dtl = new SendTranDtlRequest();
            dtl.setTranCrteDt(eventDt);
            dtl.setEventId(envelope.getEventId());
            dtl.setEventTs(eventDt);
            dtl.setEventCorltnId(envelope.getCorrelationId());
            dtl.setCrteUserNam(SYSTEM_USER);
            dtl.setUpdtUserNam(SYSTEM_USER);
            req.setTranDtl(dtl);
        }
        applyMappings(sm.getTranDtl(), txn, dtl);
    }

    private void overlayRecipDtl(SourceMapping sm,
                                 TransactionEventAxonMessage txn,
                                 SendTransactionRequest req,
                                 LocalDateTime eventDt) {
        SendRecipDtlRequest recip = req.getRecipDtl();
        if (recip == null) {
            recip = new SendRecipDtlRequest();
            recip.setTranCrteDt(eventDt);
            recip.setCrteUserNam(SYSTEM_USER);
            recip.setUpdtUserNam(SYSTEM_USER);
            req.setRecipDtl(recip);
        }
        applyMappings(sm.getRecipDtl(), txn, recip);
    }

    private void overlayAddrDtl(SourceMapping sm,
                                TransactionEventAxonMessage txn,
                                SendTransactionRequest req) {
        List<SendTranAddrDtlRequest> addrs = req.getAddrDtl() != null
                ? new ArrayList<>(req.getAddrDtl())
                : new ArrayList<>();
        for (AddrDtlGroup group : sm.getAddrDtl()) {
            overlayAddrDtlGroup(group, txn, addrs);
        }
        req.setAddrDtl(addrs.isEmpty() ? null : addrs);
    }

    private void overlayAddrDtlGroup(AddrDtlGroup group,
                                     TransactionEventAxonMessage txn,
                                     List<SendTranAddrDtlRequest> addrs) {
        final String addrType = group.getAddrType();
        SendTranAddrDtlRequest existing = addrs.stream()
                .filter(a -> addrType.equalsIgnoreCase(a.getAddrType()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            applyMappings(group.getMappings(), txn, existing);
        } else {
            SendTranAddrDtlRequest addr = new SendTranAddrDtlRequest();
            addr.setAddrType(addrType);
            addr.setCrteUserNam(SYSTEM_USER);
            addr.setUpdtUserNam(SYSTEM_USER);
            if (applyMappings(group.getMappings(), txn, addr) > 0) {
                addrs.add(addr);
            }
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Generic field-mapping entry point — applies {@code mappings} from any
     * reflection-readable {@code source} POJO to any reflection-writable
     * {@code target} POJO. Returns the populated target for fluent use.
     *
     * <p>Used by the CLEARING / SETTLEMENT flows to drive
     * {@link com.poc.transactions_consumer_canonical.dto.SendTranClrgSetlmtRequest}
     * directly from {@link TransactionEventAxonMessage} via the
     * {@code clrgSetlmt:} block in the event YAML.
     */
    public <T> T applyTo(List<FieldMapping> mappings, Object source, T target) {
        applyMappings(mappings, source, target);
        return target;
    }

    /**
     * Applies each {@link FieldMapping} in the list to {@code target}.
     *
     * @return number of fields that were successfully written
     */
    private int applyMappings(List<FieldMapping> mappings,
                               Object source,
                               Object target) {
        if (mappings == null) return 0;
        int count = 0;
        for (FieldMapping fm : mappings) {
            try {
                Object value = safeRead(source, fm.getSource());
                String str = value == null ? "" : value.toString().trim();
                if (str.isEmpty()) continue;

                boolean written = writeField(target, fm.getTarget(), value);
                if (written) count++;
            } catch (RuntimeException e) {
                log.debug("[ENGINE] Skipped mapping {}->{}: {}", fm.getSource(), fm.getTarget(), e.getMessage());
            }
        }
        return count;
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    /**
     * Reads {@code path} from {@code source}, traversing nested objects via
     * dot notation. For example {@code "sendingAccountEligible.eligible"}
     * is resolved as {@code source.getSendingAccountEligible().getEligible()}.
     * Each segment supports both {@code getXxx()} and {@code isXxx()} accessors.
     * Returns {@code null} at the first null segment or unresolvable getter.
     */
    private Object safeRead(Object source, String path) {
        if (source == null || path == null || path.isBlank()) return null;

        Object current = source;
        for (String segment : path.split("\\.")) {
            if (current == null) return null;
            current = readSingle(current, segment);
        }
        return current;
    }

    private Object readSingle(Object target, String fieldName) {
        try {
            String getter = "get" + capitalize(fieldName);
            return target.getClass().getMethod(getter).invoke(target);
        } catch (NoSuchMethodException _) {
            // try boolean-style "is" prefix for primitive booleans
            try {
                String getter = "is" + capitalize(fieldName);
                return target.getClass().getMethod(getter).invoke(target);
            } catch (ReflectiveOperationException _) {
                log.debug("[ENGINE] No getter for source field '{}' on {}", fieldName,
                        target.getClass().getSimpleName());
                return null;
            }
        } catch (ReflectiveOperationException e) {
            log.debug("[ENGINE] Failed to read source field '{}': {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Finds the setter for {@code fieldName} on {@code target}, coerces {@code value}
     * to the setter's parameter type, and invokes it.
     *
     * @return {@code true} if the setter was found and invoked successfully
     */
    private boolean writeField(Object target, String fieldName, Object value) {
        String setterName = "set" + capitalize(fieldName);
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(setterName) || method.getParameterCount() != 1) continue;
            Class<?> paramType = method.getParameterTypes()[0];
            Object coerced = coerce(value, paramType);
            if (coerced == null) return false;
            try {
                method.invoke(target, coerced);
                return true;
            } catch (ReflectiveOperationException e) {
                log.debug("[ENGINE] setter {}#{} threw: {}", target.getClass().getSimpleName(),
                        setterName, e.getMessage());
                return false;
            }
        }
        log.debug("[ENGINE] No setter '{}' found on {}", setterName, target.getClass().getSimpleName());
        return false;
    }

    // ── Type coercion ─────────────────────────────────────────────────────────

    private Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        String str = value.toString().trim();
        if (str.isEmpty()) return null;

        try {
            if (String.class == targetType)                              return str;
            if (BigDecimal.class == targetType)                          return new BigDecimal(str);
            if (LocalDate.class == targetType)                           return parseDate(str);
            if (LocalDateTime.class == targetType)                       return LocalDateTime.parse(str);
            if (Long.class == targetType || long.class == targetType)    return Long.parseLong(str);
            if (Integer.class == targetType || int.class == targetType)  return Integer.parseInt(str);
            if (Boolean.class == targetType || boolean.class == targetType) {
                return !"0".equals(str) && !"false".equalsIgnoreCase(str);
            }
        } catch (Exception e) {
            log.debug("[ENGINE] Cannot coerce '{}' to {}: {}", str, targetType.getSimpleName(), e.getMessage());
        }
        return null;
    }

    private LocalDate parseDate(String str) {
        // Accept ISO datetime strings by taking the date portion only
        return LocalDate.parse(str.length() > 10 ? str.substring(0, 10) : str);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static LocalDateTime epochToUtc(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean hasEntries(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
