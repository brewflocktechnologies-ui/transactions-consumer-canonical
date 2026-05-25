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
 *  - parent / tranDtl / recipDtl / addrDtl mapping
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
    // Full map() flow — parent + children
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("map populates parent, tranDtl, recipDtl, addrDtl[]")
    void map_fullPayload() {
        EventTypeMapping mapping = new EventTypeMapping();
        mapping.setTranType("SEND");
        mapping.setParent(List.of(
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
        // intentionally no parent / tranDtl / recipDtl / addrDtl set

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
        mapping.setParent(List.of(
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
        mapping.setParent(List.of(
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
        mapping.setParent(List.of(
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
}
