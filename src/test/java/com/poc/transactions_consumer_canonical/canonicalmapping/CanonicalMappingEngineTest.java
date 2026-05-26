package com.poc.transactions_consumer_canonical.canonicalmapping;

import com.poc.transactions_consumer_canonical.dto.SendTransactionRequest;
import com.poc.transactions_consumer_canonical.messagesdto.AccountEligibility;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.messagesdto.TransactionEventAxonMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive unit tests for {@link CanonicalMappingEngine}. Hits every branch:
 *  - tranIdSource resolution (present, missing, blank, exception)
 *  - transaction / tranDtl / recipDtl / addrDtl mapping
 *  - nested dot-notation (sendingAccountEligible.eligible)
 *  - type coercion (String→BigDecimal/Long/LocalDate/LocalDateTime/Boolean/Integer)
 *  - boolean isXxx() vs getXxx() getter resolution
 *  - blank / null source values skipped
 *  - missing setter on target silently skipped
 *  - addrDtl group skipped when no field maps successfully
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

    // ─────────────────────────────────────────────────────────────────
    // extractTranId
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractTranId uses tranIdSource when populated")
    void extractTranId_usesSourceField() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("accountInformationId");
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAccountInformationId("ACC-INFO-99");

        assertThat(engine.extractTranId(mapping, txn, envelope())).isEqualTo("ACC-INFO-99");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "nonExistentField"})
    @DisplayName("extractTranId falls back to correlationId when tranIdSource is null")
    void extractTranId_fallsBack_whenSourceMissingBlankOrUnknown(String tranIdSource) {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource(tranIdSource);

        assertThat(engine.extractTranId(mapping, new TransactionEventAxonMessage(), envelope()))
                .isEqualTo("CORR-1");
    }

    @Test
    @DisplayName("extractTranId falls back when field exists but is blank")
    void extractTranId_fallsBack_whenFieldValueIsBlank() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranIdSource("accountInformationId");
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAccountInformationId("  ");

        assertThat(engine.extractTranId(mapping, txn, envelope())).isEqualTo("CORR-1");
    }

    // ─────────────────────────────────────────────────────────────────
    // Full map() flow — transaction + children
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("map populates transaction, tranDtl, recipDtl, addrDtl[]")
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
        AddrDtlGroup sender = new AddrDtlGroup("SENDER", List.of(
                new FieldMapping("sndrAddrLine1", "stLine1", null),
                new FieldMapping("sndrCityName",  "city",    null)
        ));
        AddrDtlGroup recipient = new AddrDtlGroup("RECIPIENT", List.of(
                new FieldMapping("rcvrAddrLine1", "stLine1", null)
        ));
        mapping.setAddrDtl(List.of(sender, recipient));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setPartnerId("PTNR-1");
        txn.setAmount(2500L);
        txn.setCurrency("USD");
        AccountEligibility elig = new AccountEligibility();
        elig.setEligible(true);
        txn.setSendingAccountEligible(elig);
        txn.setOriginalRequestPayload("{\"a\":1}");
        txn.setAcqIca("123");
        txn.setSndrFirstName("Alice");
        txn.setSndrBirthDt("1990-05-15");
        txn.setSndrAddrLine1("1 First St");
        txn.setSndrCityName("StLouis");
        txn.setRcvrAddrLine1("2 Second Ave");

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getTranType()).isEqualTo("SEND");
        assertThat(req.getCrteUserNam()).isEqualTo("SYSTEM");
        assertThat(req.getUpdtUserNam()).isEqualTo("SYSTEM");
        assertThat(req.getNonFinTxn()).isFalse();
        assertThat(req.getOrigInstId()).isEqualTo("PTNR-1");
        assertThat(req.getTranAmt()).isEqualByComparingTo("2500");
        assertThat(req.getTranCurr()).isEqualTo("USD");
        assertThat(req.getRecipElig()).isTrue();
        assertThat(req.getTranCrteDt()).isNotNull();

        assertThat(req.getTranDtl()).isNotNull();
        assertThat(req.getTranDtl().getOrigRqstPyld()).isEqualTo("{\"a\":1}");
        assertThat(req.getTranDtl().getAcqIca()).isEqualTo(123L);
        assertThat(req.getTranDtl().getEventId()).isEqualTo("EVT-1");
        assertThat(req.getTranDtl().getEventCorltnId()).isEqualTo("CORR-1");

        assertThat(req.getRecipDtl()).isNotNull();
        assertThat(req.getRecipDtl().getSendFirstNam()).isEqualTo("Alice");
        assertThat(req.getRecipDtl().getSendDob()).hasToString("1990-05-15");

        assertThat(req.getAddrDtl()).hasSize(2);
        assertThat(req.getAddrDtl().get(0).getAddrType()).isEqualTo("SENDER");
        assertThat(req.getAddrDtl().get(0).getStLine1()).isEqualTo("1 First St");
        assertThat(req.getAddrDtl().get(1).getStLine1()).isEqualTo("2 Second Ave");
    }

    @Test
    @DisplayName("addrDtl group with no populated fields is dropped from result")
    void map_addrDtl_emptyGroupDropped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null)
                )),
                // RECIPIENT mappings all reference absent source fields → no field written → dropped
                new AddrDtlGroup("RECIPIENT", List.of(
                        new FieldMapping("rcvrAddrLine1", "stLine1", null)
                ))
        ));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrAddrLine1("1 First St");
        // rcvrAddrLine1 left null

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getAddrDtl()).hasSize(1);
        assertThat(req.getAddrDtl().get(0).getAddrType()).isEqualTo("SENDER");
    }

    @Test
    @DisplayName("when every addrDtl group is empty the field is left null")
    void map_addrDtl_allEmptySetsNull() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(
                        new FieldMapping("sndrAddrLine1", "stLine1", null)
                ))
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getAddrDtl()).isNull();
    }

    @Test
    @DisplayName("child sections are absent when mappings list is null or empty")
    void map_childSections_absentWhenMappingsMissing() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        // intentionally no transaction / tranDtl / recipDtl / addrDtl set

        SendTransactionRequest req = engine.map(mapping, new TransactionEventAxonMessage(), envelope());

        assertThat(req.getTranDtl()).isNull();
        assertThat(req.getRecipDtl()).isNull();
        assertThat(req.getAddrDtl()).isNull();
        assertThat(req.getTranType()).isEqualTo("AIS");
    }

    @Test
    @DisplayName("blank source values are skipped, non-blank coerced")
    void map_blankSourceSkipped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("partnerName", "origInstNam", null),
                new FieldMapping("partnerId",   "origInstId",  null)
        ));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setPartnerName("  "); // blank → skipped
        txn.setPartnerId("PTNR-X");

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getOrigInstNam()).isNull();
        assertThat(req.getOrigInstId()).isEqualTo("PTNR-X");
    }

    @Test
    @DisplayName("unknown target field is silently skipped")
    void map_unknownTargetSkipped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("partnerId", "someUnknownTargetField", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setPartnerId("PTNR-Y");

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getOrigInstId()).isNull(); // setter does not exist for that name
    }

    @Test
    @DisplayName("nested dot-notation handles null intermediate object")
    void map_nestedDotPath_nullIntermediateReturnsNull() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("sendingAccountEligible.eligible", "recipElig", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        // sendingAccountEligible left null

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getRecipElig()).isNull();
    }

    @Test
    @DisplayName("coerce handles unparseable number gracefully")
    void map_coerce_unparseable_skipped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTranDtl(List.of(
                new FieldMapping("acqIca", "acqIca", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAcqIca("not-a-number");

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        // coercion fails → silently skipped → acqIca stays null
        assertThat(req.getTranDtl()).isNotNull();
        assertThat(req.getTranDtl().getAcqIca()).isNull();
    }

    @Test
    @DisplayName("LocalDate coercion accepts both yyyy-MM-dd and ISO datetime prefix")
    void map_dateCoercion_acceptsLongerIsoString() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setRecipDtl(List.of(
                new FieldMapping("sndrBirthDt", "sendDob", null)
        ));
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrBirthDt("1985-04-10T00:00:00");

        SendTransactionRequest req = engine.map(mapping, txn, envelope());

        assertThat(req.getRecipDtl().getSendDob()).hasToString("1985-04-10");
    }

    // ─────────────────────────────────────────────────────────────────
    // sourceMappings overlay
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no sourceMappings defined — common mappings run normally, no overlay attempted")
    void sourceMappings_notDefined_commonMappingsRunNormally() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("PAYMENT");
        mapping.setTransaction(List.of(
                new FieldMapping("partnerId", "origInstId", null),
                new FieldMapping("network",   "ntwrkCd",   null)
        ));
        // sourceMappings intentionally absent — simulates PAYMENT.yaml / FUNDING.yaml style

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setPartnerId("PTNR-1");
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource("ANY_SOURCE"); // source present but no sourceMappings block

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getOrigInstId()).isEqualTo("PTNR-1");
        assertThat(req.getNtwrkCd()).isEqualTo("VISA");
    }

    private EventTypeMapping mappingWithSourceOverrides() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setTransaction(List.of(
                new FieldMapping("correlationId", "corltnId", null)
        ));

        SourceMapping scs = new SourceMapping();
        scs.setTransaction(List.of(new FieldMapping("network",    "ntwrkCd", null)));

        SourceMapping ais = new SourceMapping();
        ais.setTransaction(List.of(new FieldMapping("networkSrc", "ntwrkCd", null)));

        mapping.setSourceMappings(java.util.Map.of(
                "SEND_COMMON_SERVICES", scs,
                "AIS_SERVICE",          ais
        ));
        return mapping;
    }

    @Test
    @DisplayName("sourceMappings: SEND_COMMON_SERVICES maps 'network' to ntwrkCd")
    void sourceMappings_sendCommonServices_mapsNetworkField() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource("SEND_COMMON_SERVICES");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isEqualTo("VISA");
    }

    @Test
    @DisplayName("sourceMappings: AIS_SERVICE maps 'networkSrc' to ntwrkCd")
    void sourceMappings_aisService_mapsNetworkSrcField() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setNetworkSrc("MASTERCARD");

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isEqualTo("MASTERCARD");
    }

    @Test
    @DisplayName("sourceMappings: source field absent in JSON leaves target null")
    void sourceMappings_missingSourceField_targetRemainsNull() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        // networkSrc not set — simulates field absent in incoming JSON

        EventEnvelope env = envelope();
        env.setEventSource("AIS_SERVICE");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isNull();
    }

    @Test
    @DisplayName("sourceMappings: common mappings still apply regardless of source")
    void sourceMappings_commonMappingsAlwaysApplied() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setCorrelationId("CORR-42");
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource("SEND_COMMON_SERVICES");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getCorltnId()).isEqualTo("CORR-42");
        assertThat(req.getNtwrkCd()).isEqualTo("VISA");
    }

    @Test
    @DisplayName("sourceMappings: unrecognised source skips overlay, common mappings still run")
    void sourceMappings_unknownSource_onlyCommonApplied() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setCorrelationId("CORR-99");
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource("UNKNOWN_SOURCE");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getCorltnId()).isEqualTo("CORR-99");
        assertThat(req.getNtwrkCd()).isNull(); // no source-specific mapping ran
    }

    @Test
    @DisplayName("sourceMappings: lookup is case-insensitive")
    void sourceMappings_caseInsensitiveLookup() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setNetwork("AMEX");

        EventEnvelope env = envelope();
        env.setEventSource("send_common_services"); // lower-case

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isEqualTo("AMEX");
    }

    @Test
    @DisplayName("sourceMappings: null eventSource skips overlay entirely")
    void sourceMappings_nullEventSource_skipsOverlay() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource(null); // null → resolveSourceMapping returns null

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isNull(); // overlay not applied
    }

    @Test
    @DisplayName("sourceMappings: blank eventSource skips overlay entirely")
    void sourceMappings_blankEventSource_skipsOverlay() {
        EventTypeMapping mapping = mappingWithSourceOverrides();

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setNetwork("VISA");

        EventEnvelope env = envelope();
        env.setEventSource("   "); // blank → resolveSourceMapping returns null

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getNtwrkCd()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // applyTo() public API
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyTo maps source fields onto the supplied target and returns it")
    void applyTo_mapsFieldsAndReturnsTarget() {
        List<FieldMapping> mappings = List.of(
                new FieldMapping("partnerId", "origInstId", null),
                new FieldMapping("currency",  "tranCurr",   null)
        );
        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setPartnerId("PTNR-APT");
        txn.setCurrency("EUR");

        SendTransactionRequest req = new SendTransactionRequest();
        SendTransactionRequest result = engine.applyTo(mappings, txn, req);

        assertThat(result).isSameAs(req);
        assertThat(result.getOrigInstId()).isEqualTo("PTNR-APT");
        assertThat(result.getTranCurr()).isEqualTo("EUR");
    }

    // ─────────────────────────────────────────────────────────────────
    // applySourceOverlay — tranDtl branches
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("source overlay creates tranDtl when req has none")
    void sourceOverlay_tranDtl_createsWhenAbsent() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        // No common tranDtl → req.getTranDtl() will be null before overlay
        SourceMapping sm = new SourceMapping();
        sm.setTranDtl(List.of(new FieldMapping("acqIca", "paymtRef", null)));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAcqIca("REF-001");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getTranDtl()).isNotNull();
        assertThat(req.getTranDtl().getPaymtRef()).isEqualTo("REF-001");
    }

    @Test
    @DisplayName("source overlay appends to existing tranDtl without replacing it")
    void sourceOverlay_tranDtl_appendsToExisting() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        // Common tranDtl sets paymtType
        mapping.setTranDtl(List.of(new FieldMapping("acqIca", "paymtType", null)));
        // Overlay adds paymtRef on top
        SourceMapping sm = new SourceMapping();
        sm.setTranDtl(List.of(new FieldMapping("currency", "paymtRef", null)));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setAcqIca("PAYMENT");
        txn.setCurrency("REF-002");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getTranDtl()).isNotNull();
        assertThat(req.getTranDtl().getPaymtType()).isEqualTo("PAYMENT");
        assertThat(req.getTranDtl().getPaymtRef()).isEqualTo("REF-002");
    }

    // ─────────────────────────────────────────────────────────────────
    // applySourceOverlay — recipDtl branches
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("source overlay creates recipDtl when req has none")
    void sourceOverlay_recipDtl_createsWhenAbsent() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        SourceMapping sm = new SourceMapping();
        sm.setRecipDtl(List.of(new FieldMapping("sndrFirstName", "sendFirstNam", null)));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrFirstName("Bob");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getRecipDtl()).isNotNull();
        assertThat(req.getRecipDtl().getSendFirstNam()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("source overlay appends to existing recipDtl without replacing it")
    void sourceOverlay_recipDtl_appendsToExisting() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setRecipDtl(List.of(new FieldMapping("sndrFirstName", "sendFirstNam", null)));
        SourceMapping sm = new SourceMapping();
        sm.setRecipDtl(List.of(new FieldMapping("sndrLastName", "sendLstNam", null)));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrFirstName("Carol");
        txn.setSndrLastName("Smith");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getRecipDtl().getSendFirstNam()).isEqualTo("Carol");
        assertThat(req.getRecipDtl().getSendLstNam()).isEqualTo("Smith");
    }

    // ─────────────────────────────────────────────────────────────────
    // applySourceOverlay — addrDtl branches
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("source overlay overlays existing addrDtl group of matching type")
    void sourceOverlay_addrDtl_overlaysExistingGroup() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        // Common mapping creates a SENDER addr with stLine1
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));
        // Overlay adds city to the same SENDER group
        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("sndrCityName", "city", null)))
        ));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrAddrLine1("10 Elm St");
        txn.setSndrCityName("Springfield");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getAddrDtl()).hasSize(1);
        assertThat(req.getAddrDtl().get(0).getStLine1()).isEqualTo("10 Elm St");
        assertThat(req.getAddrDtl().get(0).getCity()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("source overlay adds a new addrDtl group when no existing matches")
    void sourceOverlay_addrDtl_addsNewGroupWhenNoMatchingType() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));
        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("RECIPIENT", List.of(new FieldMapping("rcvrAddrLine1", "stLine1", null)))
        ));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrAddrLine1("1 Sender Ave");
        txn.setRcvrAddrLine1("2 Recip Blvd");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getAddrDtl()).hasSize(2);
    }

    @Test
    @DisplayName("source overlay skips new addrDtl group when none of its fields map successfully")
    void sourceOverlay_addrDtl_skipsNewGroupWhenNoFieldsMapped() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        mapping.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));
        SourceMapping sm = new SourceMapping();
        // RECIPIENT source field is absent in txn → 0 fields mapped → group skipped
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("RECIPIENT", List.of(new FieldMapping("rcvrAddrLine1", "stLine1", null)))
        ));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrAddrLine1("1 Sender Ave");
        // rcvrAddrLine1 intentionally not set

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getAddrDtl()).hasSize(1); // only SENDER, RECIPIENT dropped
    }

    @Test
    @DisplayName("source overlay creates addrDtl from scratch when req has none")
    void sourceOverlay_addrDtl_createsListWhenReqHasNone() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        // No common addrDtl → req.getAddrDtl() will be null before overlay
        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("sndrAddrLine1", "stLine1", null)))
        ));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        txn.setSndrAddrLine1("99 New St");

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getAddrDtl()).hasSize(1);
        assertThat(req.getAddrDtl().get(0).getStLine1()).isEqualTo("99 New St");
    }

    @Test
    @DisplayName("source overlay sets addrDtl to null when every new group maps 0 fields and no existing")
    void sourceOverlay_addrDtl_setsNullWhenAllGroupsEmpty() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("AIS");
        SourceMapping sm = new SourceMapping();
        sm.setAddrDtl(List.of(
                new AddrDtlGroup("SENDER", List.of(new FieldMapping("rcvrAddrLine1", "stLine1", null)))
        ));
        mapping.setSourceMappings(java.util.Map.of("SRC", sm));

        TransactionEventAxonMessage txn = new TransactionEventAxonMessage();
        // rcvrAddrLine1 not set → 0 fields mapped → result list empty → null

        EventEnvelope env = envelope();
        env.setEventSource("SRC");

        SendTransactionRequest req = engine.map(mapping, txn, env);

        assertThat(req.getAddrDtl()).isNull();
    }
}
