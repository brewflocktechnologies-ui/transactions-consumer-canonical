package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies an {@link EventTypeMapping} to a JSON-shaped {@code Map<String,Object>}
 * (the deserialised event payload, recursively wrapped case-insensitively) and produces
 * the canonical persistence payload, also a {@code Map<String,Object>}:
 *
 * <pre>
 * {
 *   "tranId":    "...", "tranAmt": "...", "tranType": "...",  // parent fields
 *   "tranDtl":   { ... },                                     // 1:1 child
 *   "recipDtl":  { ... },                                     // 1:1 child
 *   "addrDtl":   [ { ... }, { ... } ]                         // 1:many child
 * }
 * </pre>
 *
 * <h3>Field mapping</h3>
 * For each {@link FieldMapping}:
 * <ol>
 *   <li>Read the value from the source map via the {@code source} path
 *       ({@code account.eligible} traverses nested maps). Key lookup is
 *       case-insensitive throughout.</li>
 *   <li>Write the value verbatim into the target map under the {@code target}
 *       key — the {@code target} corresponds to the {@code jsonName} of a column
 *       in {@code metadata/*.yaml}.</li>
 * </ol>
 *
 * <p>Type coercion (String → BigDecimal/LocalDate/etc.) is deferred to
 * {@link com.poc.transactions_consumer_canonical.repository.ValueConverter}
 * at JDBC bind time. Null or blank source values are silently skipped —
 * missing fields therefore land in the DB as {@code null} via the COALESCE
 * null-guard in the generated MERGE SQL.
 *
 * <h3>Fixed fields (always set)</h3>
 * <ul>
 *   <li>{@code tranType}   — literal from YAML</li>
 *   <li>{@code tranCrteDt} — derived from {@code EventEnvelope.eventTimestamp} (epoch-millis → UTC)</li>
 *   <li>{@code crteUserNam} / {@code updtUserNam} — "SYSTEM"</li>
 *   <li>{@code tranDtl.eventId}, {@code eventTs}, {@code eventCorltnId} — envelope metadata</li>
 *   <li>{@code nonFinTxn} — defaults to {@code false} (financial txn) on the parent unless overridden</li>
 * </ul>
 */
@Slf4j
@Component
public class CanonicalMappingEngine {

    private static final String SYSTEM_USER = "SYSTEM";

    private static final String KEY_TRAN_TYPE       = "tranType";
    private static final String KEY_TRAN_CRTE_DT    = "tranCrteDt";
    private static final String KEY_CRTE_USER_NAM   = "crteUserNam";
    private static final String KEY_UPDT_USER_NAM   = "updtUserNam";
    private static final String KEY_NON_FIN_TXN     = "nonFinTxn";
    private static final String KEY_EVENT_ID        = "eventId";
    private static final String KEY_EVENT_TS        = "eventTs";
    private static final String KEY_EVENT_CORLTN_ID = "eventCorltnId";
    private static final String KEY_ADDR_TYPE       = "addrType";

    /** Top-level keys reserved for the 1:1 / 1:many child sections in the canonical payload. */
    public static final String SECTION_TRAN_DTL  = "tranDtl";
    public static final String SECTION_RECIP_DTL = "recipDtl";
    public static final String SECTION_ADDR_DTL  = "addrDtl";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Derives the DB {@code TRAN_ID} from the YAML {@code tranIdSource} field,
     * falling back to {@code EventEnvelope.correlationId}.
     */
    public String extractTranId(EventTypeMapping mapping,
                                Map<String, Object> txn,
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
     * Applies the full {@link EventTypeMapping} and returns the canonical
     * {@code Map<String,Object>} payload (parent fields at top, nested children
     * under {@code tranDtl} / {@code recipDtl} / {@code addrDtl}).
     */
    public Map<String, Object> map(EventTypeMapping mapping,
                                   Map<String, Object> txn,
                                   EventEnvelope envelope) {

        LocalDateTime eventDt = epochToUtc(envelope.getEventTimestamp());

        // ── Parent ────────────────────────────────────────────────────────────
        Map<String, Object> parent = newSection();
        parent.put(KEY_TRAN_TYPE, mapping.getTranType());
        parent.put(KEY_TRAN_CRTE_DT, eventDt);
        parent.put(KEY_CRTE_USER_NAM, SYSTEM_USER);
        parent.put(KEY_UPDT_USER_NAM, SYSTEM_USER);
        parent.put(KEY_NON_FIN_TXN, false);
        applyMappings(mapping.getTransaction(), txn, parent);

        // ── tranDtl (1:1 child) ───────────────────────────────────────────────
        if (hasEntries(mapping.getTranDtl())) {
            Map<String, Object> dtl = newTranDtlSection(eventDt, envelope);
            applyMappings(mapping.getTranDtl(), txn, dtl);
            parent.put(SECTION_TRAN_DTL, dtl);
        }

        // ── recipDtl (1:1 child) ──────────────────────────────────────────────
        if (hasEntries(mapping.getRecipDtl())) {
            Map<String, Object> recip = newRecipDtlSection(eventDt);
            applyMappings(mapping.getRecipDtl(), txn, recip);
            parent.put(SECTION_RECIP_DTL, recip);
        }

        // ── addrDtl (1:many child) ────────────────────────────────────────────
        if (hasEntries(mapping.getAddrDtl())) {
            List<Map<String, Object>> addrs = new ArrayList<>();
            for (AddrDtlGroup group : mapping.getAddrDtl()) {
                Map<String, Object> addr = newAddrDtlSection(group.getAddrType());
                int mapped = applyMappings(group.getMappings(), txn, addr);
                if (mapped > 0) addrs.add(addr);
            }
            if (!addrs.isEmpty()) parent.put(SECTION_ADDR_DTL, addrs);
        }

        // ── Source-specific overlay ──────────────────────────────────────────
        SourceMapping sm = resolveSourceMapping(mapping.getSourceMappings(), envelope.getEventSource());
        if (sm != null) {
            applySourceOverlay(sm, txn, parent, eventDt, envelope);
        }
        return parent;
    }

    /**
     * Section factory for new {@code tranDtl} child maps — pre-populates the
     * envelope-derived audit fields shared by the main mapping and source overlay paths.
     */
    private Map<String, Object> newTranDtlSection(LocalDateTime eventDt, EventEnvelope envelope) {
        Map<String, Object> dtl = newSection();
        dtl.put(KEY_TRAN_CRTE_DT, eventDt);
        dtl.put(KEY_EVENT_ID, envelope.getEventId());
        dtl.put(KEY_EVENT_TS, eventDt);
        dtl.put(KEY_EVENT_CORLTN_ID, envelope.getCorrelationId());
        dtl.put(KEY_CRTE_USER_NAM, SYSTEM_USER);
        dtl.put(KEY_UPDT_USER_NAM, SYSTEM_USER);
        return dtl;
    }

    private Map<String, Object> newRecipDtlSection(LocalDateTime eventDt) {
        Map<String, Object> recip = newSection();
        recip.put(KEY_TRAN_CRTE_DT, eventDt);
        recip.put(KEY_CRTE_USER_NAM, SYSTEM_USER);
        recip.put(KEY_UPDT_USER_NAM, SYSTEM_USER);
        return recip;
    }

    private Map<String, Object> newAddrDtlSection(String addrType) {
        Map<String, Object> addr = newSection();
        addr.put(KEY_ADDR_TYPE, addrType);
        addr.put(KEY_CRTE_USER_NAM, SYSTEM_USER);
        addr.put(KEY_UPDT_USER_NAM, SYSTEM_USER);
        return addr;
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
     * Applies source-specific mappings on top of already-populated child sections.
     * Missing source fields in the JSON are silently skipped (target retains its value).
     */
    private void applySourceOverlay(SourceMapping sm,
                                    Map<String, Object> txn,
                                    Map<String, Object> parent,
                                    LocalDateTime eventDt,
                                    EventEnvelope envelope) {
        if (hasEntries(sm.getTransaction())) applyMappings(sm.getTransaction(), txn, parent);
        if (hasEntries(sm.getTranDtl()))     overlayTranDtl(sm, txn, parent, eventDt, envelope);
        if (hasEntries(sm.getRecipDtl()))    overlayRecipDtl(sm, txn, parent, eventDt);
        if (hasEntries(sm.getAddrDtl()))     overlayAddrDtl(sm, txn, parent);
    }

    @SuppressWarnings("unchecked")
    private void overlayTranDtl(SourceMapping sm,
                                Map<String, Object> txn,
                                Map<String, Object> parent,
                                LocalDateTime eventDt,
                                EventEnvelope envelope) {
        Map<String, Object> dtl = (Map<String, Object>) parent.get(SECTION_TRAN_DTL);
        if (dtl == null) {
            dtl = newTranDtlSection(eventDt, envelope);
            parent.put(SECTION_TRAN_DTL, dtl);
        }
        applyMappings(sm.getTranDtl(), txn, dtl);
    }

    @SuppressWarnings("unchecked")
    private void overlayRecipDtl(SourceMapping sm,
                                 Map<String, Object> txn,
                                 Map<String, Object> parent,
                                 LocalDateTime eventDt) {
        Map<String, Object> recip = (Map<String, Object>) parent.get(SECTION_RECIP_DTL);
        if (recip == null) {
            recip = newRecipDtlSection(eventDt);
            parent.put(SECTION_RECIP_DTL, recip);
        }
        applyMappings(sm.getRecipDtl(), txn, recip);
    }

    @SuppressWarnings("unchecked")
    private void overlayAddrDtl(SourceMapping sm,
                                Map<String, Object> txn,
                                Map<String, Object> parent) {
        List<Map<String, Object>> addrs = parent.get(SECTION_ADDR_DTL) instanceof List<?> list
                ? new ArrayList<>((List<Map<String, Object>>) list)
                : new ArrayList<>();
        for (AddrDtlGroup group : sm.getAddrDtl()) {
            overlayAddrDtlGroup(group, txn, addrs);
        }
        if (addrs.isEmpty()) parent.remove(SECTION_ADDR_DTL);
        else                 parent.put(SECTION_ADDR_DTL, addrs);
    }

    private void overlayAddrDtlGroup(AddrDtlGroup group,
                                     Map<String, Object> txn,
                                     List<Map<String, Object>> addrs) {
        final String addrType = group.getAddrType();
        Map<String, Object> existing = addrs.stream()
                .filter(a -> addrType.equalsIgnoreCase(String.valueOf(a.get(KEY_ADDR_TYPE))))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            applyMappings(group.getMappings(), txn, existing);
        } else {
            Map<String, Object> addr = newAddrDtlSection(addrType);
            if (applyMappings(group.getMappings(), txn, addr) > 0) {
                addrs.add(addr);
            }
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Generic field-mapping entry point — applies {@code mappings} from the source
     * payload to any {@code Map<String,Object>} target. Returns the populated
     * target for fluent use.
     *
     * <p>Used by the CLEARING / SETTLEMENT flows to drive the 5th-table
     * ({@code SEND_TRAN_CLRG_SETLMT}) payload directly from the event JSON via
     * the {@code clrgSetlmt:} block in the event YAML.
     */
    public Map<String, Object> applyTo(List<FieldMapping> mappings,
                                       Map<String, Object> source,
                                       Map<String, Object> target) {
        applyMappings(mappings, source, target);
        return target;
    }

    /**
     * Applies each {@link FieldMapping} in the list to {@code target}.
     *
     * @return number of fields that were successfully written
     */
    private int applyMappings(List<FieldMapping> mappings,
                              Map<String, Object> source,
                              Map<String, Object> target) {
        if (mappings == null) return 0;
        int count = 0;
        for (FieldMapping fm : mappings) {
            try {
                Object value = safeRead(source, fm.getSource());
                if (isNullOrBlank(value)) continue;
                target.put(fm.getTarget(), value);
                count++;
            } catch (RuntimeException e) {
                log.debug("[ENGINE] Skipped mapping {}->{}: {}", fm.getSource(), fm.getTarget(), e.getMessage());
            }
        }
        return count;
    }

    /** Returns true when {@code value} is null or a blank String — both are skipped during mapping. */
    private static boolean isNullOrBlank(Object value) {
        if (value == null) return true;
        return value instanceof String s && s.trim().isEmpty();
    }

    // ── Map traversal ─────────────────────────────────────────────────────────

    /**
     * Reads {@code path} from the source {@code Map}, traversing nested objects
     * via dot notation. For example {@code "sendingAccountEligible.eligible"}
     * is resolved as {@code source.get("sendingAccountEligible").get("eligible")}.
     * Returns {@code null} at the first null segment or non-{@link Map} segment.
     *
     * <p>Source maps are expected to be {@code LinkedCaseInsensitiveMap} instances
     * (wrapped at the consumer boundary via
     * {@link CaseInsensitiveJsonMap#wrapMap(Map)}), so lookups are
     * case-insensitive by construction.
     */
    private Object safeRead(Map<String, Object> source, String path) {
        if (source == null || path == null || path.isBlank()) return null;

        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(segment);
        }
        return current;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static Map<String, Object> newSection() {
        return new LinkedHashMap<>();
    }

    private static LocalDateTime epochToUtc(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static boolean hasEntries(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
