package com.poc.transactions_consumer_canonical.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingRegistry;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalRuleEngine;
import com.poc.transactions_consumer_canonical.canonicalmapping.CaseInsensitiveJsonMap;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventPayloadSanitizer;
import com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping;
import com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import com.poc.transactions_consumer_canonical.repository.SendRecipDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranAddrDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTransactionRepository;
import com.poc.transactions_consumer_canonical.service.ClearingEventService;
import com.poc.transactions_consumer_canonical.service.SendTransactionService;
import com.poc.transactions_consumer_canonical.service.SendTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end verification that an AIS_COMPLETE event flows through the new
 * Map-based pipeline and lands as the correct typed Model rows at the
 * persistence layer.
 *
 * <p>Wires together <em>real</em> components — {@link CanonicalMappingRegistry}
 * (loading {@code classpath:canonical-mappings/*.yaml}),
 * {@link EventPayloadSanitizer}, {@link CanonicalRuleEngine},
 * {@link CanonicalMappingEngine}, and the real {@link SendTransactionServiceImpl}
 * — while mocking the per-table repositories so the test runs without a database.
 *
 * <p>The captured argument to each repository's {@code upsert(...)} is the
 * typed entity Model produced by Jackson {@code convertValue} from the
 * canonical {@code Map<String,Object>} the engine emitted. Asserting on those
 * Models proves the full source-JSON → canonical-Map → typed-Model →
 * repository chain works for AIS.
 */
class AISEndToEndVerificationTest {

    private static final String AIS_TRAN_ID  = "tran_complete-aaaa-bbbb-cccc-000000000001";
    private static final String AIS_EVENT_ID = "evt_complete-aaaa-bbbb-cccc-000000000001";
    private static final String AIS_CORR_ID  = "corr_complete-aaaa-bbbb-cccc-00000001";
    private static final String AIS_FIXTURE_PATH = "/testdata/AIS/AIS_COMPLETE.json";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private CanonicalMappingRegistry registry;
    private CanonicalRuleEngine ruleEngine;
    private CanonicalMappingEngine mappingEngine;
    private ObjectMapper objectMapper;

    private SendTransactionRepository txnRepo;
    private SendTranDtlRepository dtlRepo;
    private SendRecipDtlRepository recipRepo;
    private SendTranAddrDtlRepository addrRepo;
    private GenericTableRepository genericRepo;
    private ClearingEventService clearingService;

    private KafkaCanonicalConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper  = new ObjectMapper().registerModule(new JavaTimeModule());

        // Real registry, loaded from the real AIS.yaml on the classpath
        registry = new CanonicalMappingRegistry();
        registry.load();

        EventPayloadSanitizer sanitizer = new EventPayloadSanitizer(objectMapper);
        ruleEngine    = new CanonicalRuleEngine(objectMapper);
        mappingEngine = new CanonicalMappingEngine();

        txnRepo     = mock(SendTransactionRepository.class);
        dtlRepo     = mock(SendTranDtlRepository.class);
        recipRepo   = mock(SendRecipDtlRepository.class);
        addrRepo    = mock(SendTranAddrDtlRepository.class);
        genericRepo = mock(GenericTableRepository.class);
        clearingService = mock(ClearingEventService.class);

        // self-call route from upsert → findById needs a non-empty parent + child reads
        when(genericRepo.findByPk(anyString(), anyString())).thenReturn(Optional.empty());
        when(txnRepo.findById(anyString())).thenAnswer(inv ->
                Optional.of(SendTransaction.builder().tranId(inv.getArgument(0)).build()));
        when(dtlRepo.findByTranId(anyString())).thenReturn(Optional.empty());
        when(recipRepo.findByTranId(anyString())).thenReturn(Optional.empty());
        when(addrRepo.findByTranId(anyString())).thenReturn(List.of());

        SendTransactionServiceImpl impl = new SendTransactionServiceImpl(
                txnRepo, dtlRepo, recipRepo, addrRepo, genericRepo, objectMapper);
        impl.setSelf(impl); // route the self.findById() inside upsert() back through this same instance
        SendTransactionService service = impl;

        consumer = new KafkaCanonicalConsumer(
                objectMapper, registry, ruleEngine, sanitizer, mappingEngine, service, clearingService);
    }

    private String loadEnvelopeJson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(AIS_FIXTURE_PATH)) {
            assertThat(in).as("AIS_COMPLETE.json must be on the test classpath").isNotNull();
            return new String(in.readAllBytes());
        }
    }

    private Map<String, Object> wrappedPayload(EventEnvelope envelope) throws IOException {
        return CaseInsensitiveJsonMap.wrapMap(
                objectMapper.readValue(envelope.getEventPayload(), MAP_TYPE));
    }

    @Test
    @DisplayName("AIS_COMPLETE envelope routes through the pipeline and persists every section")
    @SuppressWarnings("java:S5961") // assertion-rich e2e check by design — one test, one fixture
    void aisCompleteEventPersistsAllSections() throws Exception {
        consumer.consume(loadEnvelopeJson());

        // ── parent SEND_TRANSACTIONS row ──────────────────────────────────
        ArgumentCaptor<SendTransaction> txnCaptor = ArgumentCaptor.forClass(SendTransaction.class);
        verify(txnRepo).upsert(txnCaptor.capture());
        SendTransaction parent = txnCaptor.getValue();

        // tranId = accountInformationId from the payload (per tranIdSource in AIS.yaml)
        assertThat(parent.getTranId()).isEqualTo(AIS_TRAN_ID);
        assertThat(parent.getTranType()).isEqualTo("AIS");
        assertThat(parent.getCurStat()).isEqualTo("APPROVED");
        assertThat(parent.getOrigStat()).isEqualTo("APPROVED");
        assertThat(parent.getCorltnId()).isEqualTo(AIS_CORR_ID);
        assertThat(parent.getTranInitId()).isEqualTo(AIS_TRAN_ID);
        assertThat(parent.getRefId()).isEqualTo("rqst_E2EC-FULL-COVR-0001");
        assertThat(parent.getCustRefNum()).isEqualTo("CUST-REF-NUM-0001");
        assertThat(parent.getMsgType()).isEqualTo("0100");
        assertThat(parent.getSwSerNum()).isEqualTo("SW-SER-0001");
        assertThat(parent.getBnkntRefNum()).isEqualTo("NTWRK-REF-NUM-0001");
        assertThat(parent.getOrigInstId()).isEqualTo("ptnr_complete_e2e_full_coverage");
        assertThat(parent.getOrigInstNam()).isEqualTo("E2E Complete Coverage Partner");
        assertThat(parent.getTranInitNam()).isEqualTo("Jane Q Public");
        assertThat(parent.getTranfrAcptNam()).isEqualTo("Acme Transfer Acceptor Inc");
        assertThat(parent.getTranfrAcptId()).isEqualTo("TRA-ACPT-ID-0001");
        assertThat(parent.getTranAmt()).isEqualByComparingTo("12345");
        assertThat(parent.getTranCurr()).isEqualTo("USD");
        assertThat(parent.getSendAcct()).isEqualTo("pan:9911880000000095;exp=2077-08;cvc=123");
        assertThat(parent.getAcctNum()).isEqualTo("9911880000000095");
        assertThat(parent.getAcctType()).isEqualTo("DEBIT");
        assertThat(parent.getAcctHoldNam()).isEqualTo("Jane Q Public");
        assertThat(parent.getNtwrkRespCd()).isEqualTo("00");
        assertThat(parent.getCvcStat()).isEqualTo("MATCH");
        assertThat(parent.getCvcRespCd()).isEqualTo("M");
        assertThat(parent.getNamStat()).isEqualTo("MATCH");
        assertThat(parent.getFundAvail()).isEqualTo("NEXT_BUSINESS_DAY");
        assertThat(parent.getUseCase()).isEqualTo("P2P");
        assertThat(parent.getCrteUserNam()).isEqualTo("SYSTEM");
        assertThat(parent.getUpdtUserNam()).isEqualTo("SYSTEM");
        // nested dot-notation
        assertThat(parent.getRecipElig()).isTrue();
        assertThat(parent.getErrCd()).isEqualTo("ACCOUNT_ELIGIBLE");
        assertThat(parent.getErrCdDesc()).isEqualTo("Account eligible for send transaction");
        // source-specific overlay: SEND_COMMON_SERVICES → network field → ntwrkCd
        assertThat(parent.getNtwrkCd()).isEqualTo("MasterCard");
        // financial txn default
        assertThat(parent.getNonFinTxn()).isFalse();
        // tranCrteDt derived from envelope.eventTimestamp (epoch-millis 1760448894394 → UTC)
        assertThat(parent.getTranCrteDt()).isNotNull();

        // ── tranDtl (SEND_TRAN_DTL) ───────────────────────────────────────
        ArgumentCaptor<SendTranDtl> dtlCaptor = ArgumentCaptor.forClass(SendTranDtl.class);
        verify(dtlRepo).upsert(dtlCaptor.capture());
        SendTranDtl dtl = dtlCaptor.getValue();
        assertThat(dtl.getTranId()).isEqualTo(AIS_TRAN_ID);
        assertThat(dtl.getPaymtType()).isEqualTo("P2P");
        assertThat(dtl.getPaymtRef()).isEqualTo("rqst_E2EC-FULL-COVR-0001");
        assertThat(dtl.getUnqTranRef()).isEqualTo("UNQ-REF-NUM-0001");
        assertThat(dtl.getTranSetlAmt()).isEqualTo("12345.00");
        assertThat(dtl.getAcqCntryNam()).isEqualTo("USA");
        assertThat(dtl.getAcqIdenCd()).isEqualTo("ACQ-IDEN-001");
        assertThat(dtl.getAcqIca()).isEqualTo(1234567890L);
        assertThat(dtl.getFundSrc()).isEqualTo("DEBIT");
        assertThat(dtl.getMerchCatCd()).isEqualTo("6010");
        assertThat(dtl.getTranPrps()).isEqualTo("PERSON_TO_PERSON");
        assertThat(dtl.getTranTypeIndCd()).isEqualTo("00");
        assertThat(dtl.getIchgRateDsgn()).isEqualTo("A");
        assertThat(dtl.getRegulatedRateTypeCd()).isEqualTo("R");
        assertThat(dtl.getPointServIntrctn()).isEqualTo("ECOMMERCE");
        assertThat(dtl.getBncGtwyRqst()).contains("BNC_GTWY_REQUEST_SAMPLE_CONTENT");
        assertThat(dtl.getBncGtwyResp()).contains("BNC_GTWY_RESPONSE_SAMPLE_CONTENT");
        assertThat(dtl.getOrigRqstPyld()).contains("ptnr_complete_e2e_full_coverage");
        assertThat(dtl.getOrigRespPyld()).contains("MASTERCARD");
        assertThat(dtl.getTranfrAcptNam()).isEqualTo("Acme Transfer Acceptor Inc");
        assertThat(dtl.getTranfrAcptId()).isEqualTo("TRA-ACPT-ID-0001");
        assertThat(dtl.getTranfrTrmlId()).isEqualTo("TERM-0001");
        assertThat(dtl.getTranfrAcptStLine1()).isEqualTo("100 Acceptor Way");
        assertThat(dtl.getTranfrAcptStLine2()).isEqualTo("Suite 200");
        assertThat(dtl.getTranfrAcptCity()).isEqualTo("O'Fallon");
        assertThat(dtl.getTranfrAcptSt()).isEqualTo("MO");
        assertThat(dtl.getTranfrAcptCntryNam()).isEqualTo("USA");
        assertThat(dtl.getTranfrAcptPostCd()).isEqualTo("63368");
        assertThat(dtl.getTranfrAcptMpgId()).isEqualTo("MPG-001");
        assertThat(dtl.getTranfrAcptMerchValue()).isEqualTo("MVV-001");
        assertThat(dtl.getMcAssgnMerch()).isEqualTo("MC-ASSGN-001");
        assertThat(dtl.getProcId()).isEqualTo("PROC-001");
        assertThat(dtl.getPaymtFacltrId()).isEqualTo("PFAC-001");
        assertThat(dtl.getSubMerchId()).isEqualTo("SUBM-001");
        assertThat(dtl.getCvcRespDesc()).isEqualTo("CVC match");
        assertThat(dtl.getMsgVersion()).isEqualTo("v1");
        // Envelope-derived audit
        assertThat(dtl.getEventId()).isEqualTo(AIS_EVENT_ID);
        assertThat(dtl.getEventCorltnId()).isEqualTo(AIS_CORR_ID);
        assertThat(dtl.getCrteUserNam()).isEqualTo("SYSTEM");

        // ── recipDtl (SEND_RECIP_DTL) ─────────────────────────────────────
        ArgumentCaptor<SendRecipDtl> recipCaptor = ArgumentCaptor.forClass(SendRecipDtl.class);
        verify(recipRepo).upsert(recipCaptor.capture());
        SendRecipDtl recip = recipCaptor.getValue();
        assertThat(recip.getTranId()).isEqualTo(AIS_TRAN_ID);
        // Sender side
        assertThat(recip.getSendFirstNam()).isEqualTo("Alice");
        assertThat(recip.getSendMidNam()).isEqualTo("M");
        assertThat(recip.getSendLstNam()).isEqualTo("Sender");
        assertThat(recip.getSendPhn()).isEqualTo("+15551112222");
        assertThat(recip.getSendEmail()).isEqualTo("alice.sender@example.com");
        assertThat(recip.getSendDob()).hasToString("1985-04-10");
        assertThat(recip.getSendNatl()).isEqualTo("US");
        assertThat(recip.getSendBirthCntryNam()).isEqualTo("USA");
        assertThat(recip.getSendAcctNum()).isEqualTo("9911880000000095");
        assertThat(recip.getSendAcctUri()).isEqualTo("pan:9911880000000095;exp=2077-08");
        assertThat(recip.getSendAcctNumType()).isEqualTo("PAN");
        assertThat(recip.getSendCardNum()).isEqualTo("9911880000000095");
        assertThat(recip.getSendCardExpirDt()).isEqualTo("2077-08");
        assertThat(recip.getSendGovtIdUri()).isEqualTo("govtid:SSN:***-**-1234");
        assertThat(recip.getSendStLine1()).isEqualTo("123 Sender St");
        assertThat(recip.getSendStLine2()).isEqualTo("Apt 4B");
        assertThat(recip.getSendCity()).isEqualTo("Saint Louis");
        assertThat(recip.getSendSt()).isEqualTo("MO");
        assertThat(recip.getSendCntryNam()).isEqualTo("USA");
        assertThat(recip.getSendPostCd()).isEqualTo("63101");
        // Receiver side
        assertThat(recip.getRecipFirstNam()).isEqualTo("Bob");
        assertThat(recip.getRecipMidNam()).isEqualTo("R");
        assertThat(recip.getRecipLstNam()).isEqualTo("Receiver");
        assertThat(recip.getRecipPhn()).isEqualTo("+15553334444");
        assertThat(recip.getRecipEmail()).isEqualTo("bob.receiver@example.com");
        assertThat(recip.getRecipDob()).hasToString("1990-11-22");
        assertThat(recip.getRecipNatl()).isEqualTo("US");
        assertThat(recip.getRecipBirthCntryNam()).isEqualTo("USA");
        assertThat(recip.getRecipAcctUri()).isEqualTo("pan:4055011111111111;exp=2078-12");
        assertThat(recip.getRecipAcctNumType()).isEqualTo("PAN");
        assertThat(recip.getRecipCardNum()).isEqualTo("4055011111111111");
        assertThat(recip.getRecipCardExpirDt()).isEqualTo("2078-12");
        assertThat(recip.getRecipGovtIdUri()).isEqualTo("govtid:SSN:***-**-5678");
        assertThat(recip.getRecipStLine1()).isEqualTo("456 Receiver Ave");
        assertThat(recip.getRecipStLine2()).isEqualTo("Unit 9");
        assertThat(recip.getRecipCity()).isEqualTo("Chicago");
        assertThat(recip.getRecipSt()).isEqualTo("IL");
        assertThat(recip.getRecipCntryNam()).isEqualTo("USA");
        assertThat(recip.getRecipPostCd()).isEqualTo("60601");

        // ── addrDtl (SEND_TRAN_ADDR_DTL) ──────────────────────────────────
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SendTranAddrDtl>> addrCaptor = ArgumentCaptor.forClass(List.class);
        verify(addrRepo).mergeAll(addrCaptor.capture());
        List<SendTranAddrDtl> addrs = addrCaptor.getValue();
        assertThat(addrs).hasSize(2);

        SendTranAddrDtl sender = addrs.stream()
                .filter(a -> "SENDER".equals(a.getAddrType())).findFirst().orElseThrow();
        assertThat(sender.getTranId()).isEqualTo(AIS_TRAN_ID);
        assertThat(sender.getStLine1()).isEqualTo("123 Sender St");
        assertThat(sender.getStLine2()).isEqualTo("Apt 4B");
        assertThat(sender.getCity()).isEqualTo("Saint Louis");
        assertThat(sender.getSt()).isEqualTo("MO");
        assertThat(sender.getCntryNam()).isEqualTo("USA");
        assertThat(sender.getPostCd()).isEqualTo("63101");
        assertThat(sender.getAddrStat()).isEqualTo("MATCH");
        assertThat(sender.getPostCdStat()).isEqualTo("MATCH");
        assertThat(sender.getId()).isNotBlank(); // UUID generated when missing

        SendTranAddrDtl recipient = addrs.stream()
                .filter(a -> "RECIPIENT".equals(a.getAddrType())).findFirst().orElseThrow();
        assertThat(recipient.getStLine1()).isEqualTo("456 Receiver Ave");
        assertThat(recipient.getStLine2()).isEqualTo("Unit 9");
        assertThat(recipient.getCity()).isEqualTo("Chicago");
        assertThat(recipient.getSt()).isEqualTo("IL");
        assertThat(recipient.getCntryNam()).isEqualTo("USA");
        assertThat(recipient.getPostCd()).isEqualTo("60601");

        // ── prune call uses the same id set as the merge call ─────────────
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keepIds = ArgumentCaptor.forClass(List.class);
        verify(addrRepo).deleteByTranIdNotIn(eq(AIS_TRAN_ID), keepIds.capture());
        assertThat(keepIds.getValue()).hasSize(2);
        assertThat(keepIds.getValue()).contains(sender.getId(), recipient.getId());

        // ── the 5th-table service must NOT be touched (AIS has no pipeline:CLRG_SETLMT) ──
        verify(clearingService, never()).upsertClearing(anyString(), any());
        verify(clearingService, never()).upsertSettlement(anyString(), any());
    }

    @Test
    @DisplayName("AIS_COMPLETE: pipeline is not CLRG_SETLMT so ClearingEventService is bypassed")
    void aisCompleteEventDoesNotTouchClearingService() {
        Optional<EventTypeMapping> mappingOpt = registry.findByEventName("AIS");
        assertThat(mappingOpt).isPresent();
        // sanity check on the loaded YAML — AIS is the standard 4-table path
        assertThat(mappingOpt.get().getPipeline()).isNull();
    }

    @Test
    @DisplayName("AIS canonical map produced directly by the engine matches expected jsonNames")
    @SuppressWarnings("unchecked")
    void aisCompleteEventEngineProducesExpectedCanonicalMap() throws Exception {
        // Decode the envelope and the embedded eventPayload manually so we can
        // assert on the engine's raw canonical Map (before any Map→Model conversion).
        EventEnvelope envelope = objectMapper.readValue(loadEnvelopeJson(), EventEnvelope.class);
        Map<String, Object> txn = wrappedPayload(envelope);

        EventTypeMapping mapping = registry.findByEventName(envelope.getEventName()).orElseThrow();

        Map<String, Object> canonical = mappingEngine.map(mapping, txn, envelope);

        // top-level keys we care about
        assertThat(canonical)
                .containsKey(CanonicalMappingEngine.SECTION_TRAN_DTL)
                .containsKey(CanonicalMappingEngine.SECTION_RECIP_DTL)
                .containsKey(CanonicalMappingEngine.SECTION_ADDR_DTL)
                .containsEntry("tranType", "AIS")
                .containsEntry("ntwrkCd", "MasterCard") // SEND_COMMON_SERVICES overlay
                .containsEntry("recipElig", true);
        assertThat(canonical.get("tranCrteDt")).isNotNull();

        // Case-insensitive lookup proven — payload uses original keys, our paths use camelCase
        assertThat(canonical).containsEntry("tranAmt", 12345); // Jackson parses unquoted JSON int as Integer

        Map<String, Object> dtl = (Map<String, Object>) canonical.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        assertThat(dtl)
                .containsEntry("eventId", AIS_EVENT_ID)
                .containsEntry("paymtType", "P2P");

        List<Map<String, Object>> addrs =
                (List<Map<String, Object>>) canonical.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(2);
        assertThat(addrs.get(0)).containsEntry("addrType", "SENDER");
        assertThat(addrs.get(1)).containsEntry("addrType", "RECIPIENT");
    }

    @Test
    @DisplayName("Engine output: addrDtl has both SENDER and RECIPIENT (case-insensitive group merge)")
    @SuppressWarnings("unchecked")
    void aisCompleteEventAddrDtlBothGroupsPopulated() throws Exception {
        EventEnvelope envelope = objectMapper.readValue(loadEnvelopeJson(), EventEnvelope.class);
        Map<String, Object> txn = wrappedPayload(envelope);
        EventTypeMapping mapping = registry.findByEventName(envelope.getEventName()).orElseThrow();
        Map<String, Object> canonical = mappingEngine.map(mapping, txn, envelope);

        List<Map<String, Object>> addrs =
                (List<Map<String, Object>>) canonical.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        assertThat(addrs).hasSize(2);

        Map<String, Object> sender = addrs.stream()
                .filter(a -> "SENDER".equals(a.get("addrType"))).findFirst().orElseThrow();
        assertThat(sender)
                .containsEntry("stLine1", "123 Sender St")
                .containsEntry("city", "Saint Louis")
                .containsEntry("postCd", "63101");

        Map<String, Object> recipient = addrs.stream()
                .filter(a -> "RECIPIENT".equals(a.get("addrType"))).findFirst().orElseThrow();
        assertThat(recipient)
                .containsEntry("stLine1", "456 Receiver Ave")
                .containsEntry("city", "Chicago")
                .containsEntry("postCd", "60601");
    }

    @Test
    @DisplayName("Rule engine accepts AIS_COMPLETE from SEND_COMMON_SERVICES with operation A")
    void aisCompleteEventPassesRuleEngineFilter() throws Exception {
        EventEnvelope envelope = objectMapper.readValue(loadEnvelopeJson(), EventEnvelope.class);
        EventTypeMapping mapping = registry.findByEventName(envelope.getEventName()).orElseThrow();
        assertThat(ruleEngine.shouldProcess(mapping, envelope)).isTrue();
    }

    @Test
    @DisplayName("End-to-end: a single AIS event triggers exactly one upsert per repository")
    void aisCompleteEventSingleConsumeSingleUpsertEach() throws Exception {
        consumer.consume(loadEnvelopeJson());

        verify(txnRepo).upsert(any(SendTransaction.class));
        verify(dtlRepo).upsert(any(SendTranDtl.class));
        verify(recipRepo).upsert(any(SendRecipDtl.class));
        verify(addrRepo).mergeAll(any());
        // genericRepo is only touched by findById for the optional 5th-table read on response assembly
        verify(genericRepo, never()).upsert(anyString(), any());
    }
}
