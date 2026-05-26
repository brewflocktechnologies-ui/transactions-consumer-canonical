package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Map-based {@link CanonicalMappingEngine}. Source payloads
 * are {@code Map<String,Object>} (the same shape Jackson produces from JSON);
 * the canonical output is also a {@code Map<String,Object>} keyed by the
 * {@code jsonName} of each column in {@code metadata/*.yaml}.
 */
class CanonicalMappingEngineTest {

    private CanonicalMappingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CanonicalMappingEngine();
    }

    private EventEnvelope envelope() {
        EventEnvelope e = new EventEnvelope();
        e.setEventId("EVT-1");
        e.setCorrelationId("CORR-1");
        e.setEventTimestamp(1731660600000L);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(Map<String, Object> entries) {
        return (Map<String, Object>) CaseInsensitiveJsonMap.wrap(entries);
    }

    // ─────────────────────────────────────────────────────────────────
    // extractTranId
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractTranId uses tranIdSource when populated")
    void extractTranId_usesSourceField() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("accountInformationId");
        Map<String, Object> txn = source(Map.of("accountInformationId", "ACC-INFO-99"));

        assertThat(engine.extractTranId(mapping, txn, envelope())).isEqualTo("ACC-INFO-99");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nonExistentField"})
    @DisplayName("extractTranId falls back to correlationId when tranIdSource is null / blank / unknown")
    void extractTranId_fallsBack_whenSourceMissingBlankOrUnknown(String tranIdSource) {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource(tranIdSource);

        assertThat(engine.extractTranId(mapping, source(Map.of()), envelope())).isEqualTo("CORR-1");
    }

    @Test
    @DisplayName("extractTranId falls back when field exists but is blank")
    void extractTranId_fallsBack_whenFieldValueIsBlank() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("accountInformationId");
        Map<String, Object> txn = source(Map.of("accountInformationId", "  "));

        assertThat(engine.extractTranId(mapping, txn, envelope())).isEqualTo("CORR-1");
    }

    @Test
    @DisplayName("extractTranId reads via case-insensitive key lookup")
    void extractTranId_caseInsensitive() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("tranId");
        Map<String, Object> txn = source(Map.of("TRANID", "T-99"));

        assertThat(engine.extractTranId(mapping, txn, envelope())).isEqualTo("T-99");
    }

    // ─────────────────────────────────────────────────────────────────
    // Full map() flow
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("map populates transaction, tranDtl, recipDtl, addrDtl[]")
    @SuppressWarnings("unchecked")
    void map_fullPayload() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("partnerId", "origInstId", null),
                new FieldMapping("amount",    "tranAmt",    null),
                new FieldMapping("currency",  "tranCurr",   null),
                new FieldMapping("sendingAccountEligible.eligible", "recipElig", null)
        ));
        mapping.setTranDtl(List.of(
                new FieldMapping("originalRequestPayload", "origRqstPyld", null),
                new FieldMapping("acqIca", "acqIca", null)
        ));
        mapping.setRecipDtl(List.of(
                new FieldMapping("sndrFirstName", "sendFirstNam", null),
                new FieldMapping("sndrBirthDt",   "sendDob",      null)
        ));
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null),
                        new FieldMapping("sndrCityName",  "city",    null))),
                new AddrDtlGroup("RECIPIENT", List.of(
                        new FieldMapping("rcvrAddrLine1", "stLine1", null)))
        ));

        Map<String, Object> txn = source(Map.ofEntries(
                Map.entry("partnerId",               "PTNR-1"),
                Map.entry("amount",                  "2500"),
                Map.entry("currency",                "USD"),
                Map.entry("sendingAccountEligible",  Map.of("eligible", true)),
                Map.entry("originalRequestPayload",  "<xml/>"),
                Map.entry("acqIca",                  "ACQ-001"),
                Map.entry("sndrFirstName",           "Alice"),
                Map.entry("sndrBirthDt",             "1990-05-15"),
                Map.entry("sndrAddrLine1",           "1 Main St"),
                Map.entry("sndrCityName",            "Tampa"),
                Map.entry("rcvrAddrLine1",           "9 Beach Rd")
        ));

        Map<String, Object> req = engine.map(mapping, txn, envelope());

        // Parent
        assertThat(req)
                .containsEntry("tranType", "SEND")
                .containsEntry("origInstId", "PTNR-1")
                .containsEntry("tranAmt", "2500")
                .containsEntry("tranCurr", "USD")
                .containsEntry("recipElig", true)
                .containsEntry("crteUserNam", "SYSTEM")
                .containsEntry("updtUserNam", "SYSTEM")
                .containsEntry("nonFinTxn", false)
                .containsKey("tranCrteDt");

        // tranDtl
        Map<String, Object> dtl = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        assertThat(dtl)
                .isNotNull()
                .containsEntry("origRqstPyld", "<xml/>")
                .containsEntry("acqIca", "ACQ-001")
                .containsEntry("eventId", "EVT-1")
                .containsEntry("eventCorltnId", "CORR-1")
                .containsEntry("crteUserNam", "SYSTEM");

        // recipDtl
        Map<String, Object> recip = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_RECIP_DTL);
        assertThat(recip)
                .containsEntry("sendFirstNam", "Alice")
                .containsEntry("sendDob", "1990-05-15");

        // addrDtl
        List<Map<String, Object>> addrs = (List<Map<String, Object>>) req.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(2);
        assertThat(addrs.get(0))
                .containsEntry("addrType", "SENDER")
                .containsEntry("stLine1", "1 Main St");
        assertThat(addrs.get(1))
                .containsEntry("addrType", "RECIPIENT")
                .containsEntry("stLine1", "9 Beach Rd");
    }

    @Test
    @DisplayName("map skips an addrDtl group entirely if no field matches the source")
    void map_addrGroup_skippedWhenNoSourceFieldMapped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null))),
                new AddrDtlGroup("BILLING", List.of(
                        new FieldMapping("billingLine1", "stLine1", null)))
        ));

        Map<String, Object> txn = source(Map.of("sndrAddrLine1", "1 Main St"));

        Map<String, Object> req = engine.map(mapping, txn, envelope());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addrs =
                (List<Map<String, Object>>) req.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(1);
        assertThat(addrs.get(0)).containsEntry("addrType", "SENDER");
    }

    @Test
    @DisplayName("map omits addrDtl entirely when no group has any populated source field")
    void map_addrDtl_omittedWhenAllGroupsEmpty() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));
        Map<String, Object> req = engine.map(mapping, source(Map.of()), envelope());
        assertThat(req).doesNotContainKey(CanonicalMappingEngine.SECTION_ADDR_DTL);
    }

    @Test
    @DisplayName("map skips null / blank source values silently")
    void map_skipsNullAndBlankValues() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("amount", "tranAmt", null),
                new FieldMapping("notes",  "comments", null)
        ));

        Map<String, Object> txn = new HashMap<>();
        txn.put("amount", "100");
        txn.put("notes", "   "); // blank → skipped
        Map<String, Object> req = engine.map(mapping, source(txn), envelope());

        assertThat(req)
                .containsEntry("tranAmt", "100")
                .doesNotContainKey("comments");
    }

    @Test
    @DisplayName("map skips a mapping when the source field is absent")
    void map_skipsAbsentSourceField() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("notInSource", "shouldNotAppear", null)
        ));

        Map<String, Object> req = engine.map(mapping, source(Map.of()), envelope());
        assertThat(req).doesNotContainKey("shouldNotAppear");
    }

    @Test
    @DisplayName("map traverses nested paths with case-insensitive segments")
    @SuppressWarnings("unchecked")
    void map_nestedDotPath_caseInsensitive() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("account.eligible", "recipElig", null)
        ));

        Map<String, Object> txn = source(Map.of("ACCOUNT", Map.of("ELIGIBLE", true)));
        Map<String, Object> req = engine.map(mapping, txn, envelope());

        assertThat(req).containsEntry("recipElig", true);
    }

    @Test
    @DisplayName("map returns null at the first non-Map segment in a dot path")
    void map_dotPath_stopsAtNonMapSegment() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("tranAmt.amount", "shouldNotAppear", null)
        ));
        Map<String, Object> txn = source(Map.of("tranAmt", "2500")); // not a Map → stops
        Map<String, Object> req = engine.map(mapping, txn, envelope());
        assertThat(req).doesNotContainKey("shouldNotAppear");
    }

    @Test
    @DisplayName("map populates tranDtl envelope fields even when no source-driven fields match")
    @SuppressWarnings("unchecked")
    void map_tranDtl_envelopeFieldsPopulatedEvenWithoutSourceMatches() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTranDtl(List.of(
                new FieldMapping("originalRequestPayload", "origRqstPyld", null)
        ));
        Map<String, Object> req = engine.map(mapping, source(Map.of()), envelope());
        Map<String, Object> dtl = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        assertThat(dtl)
                .containsEntry("eventId", "EVT-1")
                .containsEntry("eventCorltnId", "CORR-1")
                .doesNotContainKey("origRqstPyld"); // source absent → skipped
    }

    // ─────────────────────────────────────────────────────────────────
    // applyTo (used by the CLEARING / SETTLEMENT 5th-table flow)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyTo writes each mapping into the target map and returns it")
    void applyTo_writesEachMappingIntoTarget() {
        List<FieldMapping> mappings = List.of(
                new FieldMapping("clearingStatus", "clrgSt",   null),
                new FieldMapping("clearingDate",   "clrgDtTs", null)
        );
        Map<String, Object> txn = source(Map.of(
                "clearingStatus", "SETTLED",
                "clearingDate",   "2026-05-26T10:00:00"));
        Map<String, Object> target = new LinkedHashMap<>();

        Map<String, Object> returned = engine.applyTo(mappings, txn, target);

        assertThat(returned).isSameAs(target);
        assertThat(target)
                .containsEntry("clrgSt", "SETTLED")
                .containsEntry("clrgDtTs", "2026-05-26T10:00:00");
    }

    @Test
    @DisplayName("applyTo silently skips null source values without throwing")
    void applyTo_skipsNullValues() {
        List<FieldMapping> mappings = List.of(
                new FieldMapping("missing", "shouldNotAppear", null)
        );
        Map<String, Object> target = engine.applyTo(mappings, source(Map.of()), new LinkedHashMap<>());
        assertThat(target).doesNotContainKey("shouldNotAppear");
    }

    // ─────────────────────────────────────────────────────────────────
    // Source-specific overlays
    // ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "ais_service, OVERRIDE, ORIG",
        "'', COMMON, COMMON",
        "UNKNOWN, COMMON, COMMON"
    })
    @DisplayName("sourceMappings overlay parameterized test")
    void map_sourceOverlay_parameterized(String eventSource, String expectedNtwrkCd, String origNetworkVal) {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(new FieldMapping("network", "ntwrkCd", null)));

        SourceMapping sm = new SourceMapping();
        sm.setTransaction(List.of(new FieldMapping("networkSrc", "ntwrkCd", null)));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        Map<String, Object> txn = source(Map.of(
                "network", origNetworkVal,
                "networkSrc", "OVERRIDE"));

        EventEnvelope env = envelope();
        env.setEventSource(eventSource);

        Map<String, Object> req = engine.map(mapping, txn, env);
        assertThat(req).containsEntry("ntwrkCd", expectedNtwrkCd);
    }

    @Test
    @DisplayName("sourceMappings: tranDtl overlay creates section if main mapping didn't populate it")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_tranDtl_createsSectionIfAbsent() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");

        SourceMapping sm = new SourceMapping();
        sm.setTranDtl(List.of(new FieldMapping("paymtRef", "paymtRef", null)));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping, source(Map.of("paymtRef", "PR-X")), env);
        Map<String, Object> dtl = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        assertThat(dtl)
                .isNotNull()
                .containsEntry("paymtRef", "PR-X")
                .containsEntry("eventId", "EVT-1"); // envelope fields seeded
    }

    @Test
    @DisplayName("sourceMappings: tranDtl overlay merges into existing section without re-seeding envelope fields")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_tranDtl_mergesIntoExisting() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTranDtl(List.of(new FieldMapping("acqIca", "acqIca", null)));

        SourceMapping sm = new SourceMapping();
        sm.setTranDtl(List.of(new FieldMapping("paymtRef", "paymtRef", null)));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping,
                source(Map.of("acqIca", "ACQ-1", "paymtRef", "PR-X")), env);
        Map<String, Object> dtl = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        assertThat(dtl)
                .containsEntry("acqIca", "ACQ-1")
                .containsEntry("paymtRef", "PR-X");
    }

    @Test
    @DisplayName("sourceMappings: recipDtl overlay creates section if absent")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_recipDtl_createsSectionIfAbsent() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");

        SourceMapping sm = new SourceMapping();
        sm.setRecipDtl(List.of(new FieldMapping("sndrFirstName", "sendFirstNam", null)));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping, source(Map.of("sndrFirstName", "Alice")), env);
        Map<String, Object> recip = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_RECIP_DTL);
        assertThat(recip).containsEntry("sendFirstNam", "Alice");
    }

    @Test
    @DisplayName("sourceMappings: recipDtl overlay merges into existing section")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_recipDtl_mergesIntoExisting() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setRecipDtl(List.of(new FieldMapping("sndrLastName", "sendLstNam", null)));

        SourceMapping sm = new SourceMapping();
        sm.setRecipDtl(List.of(new FieldMapping("sndrFirstName", "sendFirstNam", null)));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping,
                source(Map.of("sndrFirstName", "Alice", "sndrLastName", "Brown")), env);
        Map<String, Object> recip = (Map<String, Object>) req.get(CanonicalMappingEngine.SECTION_RECIP_DTL);
        assertThat(recip)
                .containsEntry("sendFirstNam", "Alice")
                .containsEntry("sendLstNam", "Brown");
    }

    @Test
    @DisplayName("sourceMappings: addrDtl overlay merges into existing matching group (case-insensitive addrType)")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_addrDtl_mergesIntoExistingGroup() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));

        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("sender", List.of( // lowercase → case-insensitive
                        new FieldMapping("sndrCityName", "city", null)))
        ));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping,
                source(Map.of("sndrAddrLine1", "1 Main", "sndrCityName", "Tampa")), env);
        List<Map<String, Object>> addrs =
                (List<Map<String, Object>>) req.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(1);
        assertThat(addrs.get(0)).containsEntry("stLine1", "1 Main");
        assertThat(addrs.get(0)).containsEntry("city", "Tampa");
    }

    @Test
    @DisplayName("sourceMappings: addrDtl overlay adds a new group when no matching addrType")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_addrDtl_addsNewGroup() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));

        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("BILLING", List.of(
                        new FieldMapping("billingLine1", "stLine1", null)))
        ));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping,
                source(Map.of("sndrAddrLine1", "1 Main", "billingLine1", "5 Bill St")), env);
        List<Map<String, Object>> addrs =
                (List<Map<String, Object>>) req.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(2);
        assertThat(addrs.get(1)).containsEntry("addrType", "BILLING").containsEntry("stLine1", "5 Bill St");
    }

    @Test
    @DisplayName("sourceMappings: addrDtl overlay skips new group when no source field matches")
    @SuppressWarnings("unchecked")
    void map_sourceOverlay_addrDtl_skipsEmptyNewGroup() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");

        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("BILLING", List.of(
                        new FieldMapping("notPresent", "stLine1", null)))
        ));
        mapping.setSourceMappings(Map.of("AIS_SERVICE", sm));

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        Map<String, Object> req = engine.map(mapping, source(Map.of()), env);
        assertThat(req).doesNotContainKey(CanonicalMappingEngine.SECTION_ADDR_DTL);
    }

    // Deleted blank and unknown eventSource overlay tests as they are covered by map_sourceOverlay_parameterized

    // ─────────────────────────────────────────────────────────────────
    // Case-insensitive Map behaviour
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("case-insensitive lookup: source key uses MixedCase but path is camelCase")
    void map_caseInsensitiveLookup_mixedCaseKey() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setTransaction(List.of(
                new FieldMapping("partnerId", "origInstId", null)
        ));
        // Source uses snake_case-ish key — case-insensitive lookup still resolves it
        Map<String, Object> txn = source(Map.of("PARTNERID", "P-1"));
        Map<String, Object> req = engine.map(mapping, txn, envelope());
        assertThat(req).containsEntry("origInstId", "P-1");
    }

    @Test
    @DisplayName("CaseInsensitiveJsonMap.wrapMap recursively wraps nested maps")
    @SuppressWarnings("unchecked")
    void caseInsensitiveJsonMap_wrapsRecursively() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("Outer", Map.of("Inner", "value"));
        Map<String, Object> wrapped = CaseInsensitiveJsonMap.wrapMap(raw);
        assertThat(wrapped).isInstanceOf(LinkedCaseInsensitiveMap.class);
        Object inner = wrapped.get("OUTER");
        assertThat(inner).isInstanceOf(LinkedCaseInsensitiveMap.class);
        Map<String, Object> innerMap = (Map<String, Object>) inner;
        assertThat(innerMap).containsEntry("inner", "value");
    }

    @Test
    @DisplayName("CaseInsensitiveJsonMap.wrapMap returns empty map for null input")
    void caseInsensitiveJsonMap_nullInput_emptyMap() {
        Map<String, Object> wrapped = CaseInsensitiveJsonMap.wrapMap(null);
        assertThat(wrapped)
                .isInstanceOf(LinkedCaseInsensitiveMap.class)
                .isEmpty();
    }

    @Test
    @DisplayName("CaseInsensitiveJsonMap walks nested lists too")
    @SuppressWarnings("unchecked")
    void caseInsensitiveJsonMap_walksLists() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("Items", List.of(Map.of("Key", "v1"), Map.of("Key", "v2")));
        Map<String, Object> wrapped = CaseInsensitiveJsonMap.wrapMap(raw);
        List<Map<String, Object>> items = (List<Map<String, Object>>) wrapped.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0))
                .containsEntry("KEY", "v1");
        assertThat(items.get(1))
                .containsEntry("key", "v2");
    }

    @Test
    @DisplayName("CaseInsensitiveJsonMap passes primitives and null through unchanged")
    void caseInsensitiveJsonMap_primitivePassthrough() {
        assertThat(CaseInsensitiveJsonMap.wrap(null)).isNull();
        assertThat(CaseInsensitiveJsonMap.wrap("plain")).isEqualTo("plain");
        assertThat(CaseInsensitiveJsonMap.wrap(42)).isEqualTo(42);
    }

    @Test
    @DisplayName("CaseInsensitiveJsonMap ignores Map entries with null keys")
    void caseInsensitiveJsonMap_nullKey_skipped() {
        Map<Object, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, "ghost");
        withNullKey.put("real", "kept");
        @SuppressWarnings("unchecked")
        Map<String, Object> wrapped =
                (Map<String, Object>) CaseInsensitiveJsonMap.wrap(withNullKey);
        assertThat(wrapped).containsOnlyKeys("real");
    }

    // ─────────────────────────────────────────────────────────────────
    // Defensive behaviour
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("map handles null transaction list / null tranDtl list / null addrDtl list")
    void map_nullMappingLists_handledGracefully() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        // every list null
        Map<String, Object> req = engine.map(mapping, source(Map.of()), envelope());
        assertThat(req)
                .containsEntry("tranType", "SEND")
                .doesNotContainKey(CanonicalMappingEngine.SECTION_TRAN_DTL)
                .doesNotContainKey(CanonicalMappingEngine.SECTION_RECIP_DTL)
                .doesNotContainKey(CanonicalMappingEngine.SECTION_ADDR_DTL);
    }

    @Test
    @DisplayName("safeRead returns null when source map is null")
    void safeRead_nullSource_returnsNull() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("tranId");
        // null txn ⇒ falls back to correlationId
        assertThat(engine.extractTranId(mapping, null, envelope())).isEqualTo("CORR-1");
    }

    @Test
    @DisplayName("source map default LinkedCaseInsensitiveMap wraps deeply nested case variants")
    @SuppressWarnings("unchecked")
    void caseInsensitive_deepNesting() {
        Map<String, Object> raw = new HashMap<>();
        Map<String, Object> mid = new HashMap<>();
        Map<String, Object> deep = new HashMap<>();
        deep.put("LeafKey", "found");
        mid.put("MidKey", deep);
        raw.put("OuterKey", mid);
        Map<String, Object> wrapped = (Map<String, Object>) CaseInsensitiveJsonMap.wrap(raw);
        Map<String, Object> midRead = (Map<String, Object>) wrapped.get("outerkey");
        Map<String, Object> deepRead = (Map<String, Object>) midRead.get("MIDKEY");
        assertThat(deepRead).containsEntry("leafkey", "found");
    }

    @Test
    @DisplayName("LinkedCaseInsensitiveMap with explicit locale ensures stable behaviour")
    void linkedCaseInsensitiveMap_localeBehaviour() {
        // Spot check: explicit ROOT locale prevents the Turkish "I/ı" trap
        Map<String, Object> m = new LinkedCaseInsensitiveMap<>(4, Locale.ROOT);
        m.put("eventId", "EVT-1");
        assertThat(m).containsEntry("EVENTID", "EVT-1");
    }
}
