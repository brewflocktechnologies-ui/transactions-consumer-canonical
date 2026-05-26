package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.canonicalmapping.CanonicalMappingEngine;
import com.poc.transactions_consumer_canonical.dto.SendRecipDtlResponse;
import com.poc.transactions_consumer_canonical.dto.SendTranAddrDtlResponse;
import com.poc.transactions_consumer_canonical.dto.SendTranClrgResponse;
import com.poc.transactions_consumer_canonical.dto.SendTranDtlResponse;
import com.poc.transactions_consumer_canonical.dto.SendTranSetlmtResponse;
import com.poc.transactions_consumer_canonical.dto.SendTransactionResponse;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.model.SendRecipDtl;
import com.poc.transactions_consumer_canonical.model.SendTranAddrDtl;
import com.poc.transactions_consumer_canonical.model.SendTranDtl;
import com.poc.transactions_consumer_canonical.model.SendTransaction;
import com.poc.transactions_consumer_canonical.repository.GenericTableRepository;
import com.poc.transactions_consumer_canonical.repository.SendRecipDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranAddrDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTranDtlRepository;
import com.poc.transactions_consumer_canonical.repository.SendTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the canonical Send-Transaction upsert (parent + 1:1 / 1:many children).
 *
 * <p>The write path is invoked exclusively by the Kafka canonical pipeline; the public
 * {@link #upsert(String, Map)} accepts a {@code Map<String,Object>} payload as produced
 * by {@link CanonicalMappingEngine#map(com.poc.transactions_consumer_canonical.canonicalmapping.EventTypeMapping,
 * Map, com.poc.transactions_consumer_canonical.messagesdto.EventEnvelope)} — there is no
 * intermediate typed request DTO. Child sub-maps are converted to typed
 * {@link com.poc.transactions_consumer_canonical.model entity models} via Jackson
 * {@code convertValue} so the existing per-table repositories can stay unchanged.
 *
 * <p>The read path ({@link #findById(String)}) still reads the typed entity models
 * via the same repositories and assembles a typed {@link SendTransactionResponse}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendTransactionServiceImpl implements SendTransactionService {

    private static final String KEY_TRAN_ID    = "tranId";
    private static final String CLRG_SETLMT_ALIAS = "send-tran-clrg-setlmt";

    private final SendTransactionRepository txnRepo;
    private final SendTranDtlRepository     dtlRepo;
    private final SendRecipDtlRepository    recipRepo;
    private final SendTranAddrDtlRepository addrRepo;
    /** Metadata-driven access to SEND_TRAN_CLRG_SETLMT (no typed repository — fetched as a Map). */
    private final GenericTableRepository    genericRepo;
    /** Used by {@link #toResponse} for field-name-based copying and by upsert for Map→Model. */
    private final ObjectMapper              objectMapper;

    /**
     * Self-reference injected via setter (lazy) so {@code @Transactional} calls on
     * {@link #findById} route through the Spring proxy rather than bypassing it via
     * {@code this} (satisfies S6809; setter injection satisfies S6813).
     */
    private SendTransactionService self;

    @Autowired
    @Lazy
    public void setSelf(@NonNull SendTransactionService self) {
        this.self = self;
    }

    // ── Upsert ───────────────────────────────────────────────

    @Override
    @Transactional
    public SendTransactionResponse upsert(String tranId, Map<String, Object> canonical) {
        if (canonical == null) {
            throw new IllegalArgumentException("canonical payload must not be null");
        }
        log.info("Upserting transaction: tranId={}, tranType={}, curStat={}",
                tranId, canonical.get("tranType"), canonical.get("curStat"));

        txnRepo.upsert(toParentModel(tranId, canonical));
        upsertTranDtlIfPresent(tranId, canonical);
        upsertRecipDtlIfPresent(tranId, canonical);
        upsertAddrDtlIfPresent(tranId, canonical);

        log.info("Upsert complete for tranId={}", tranId);
        return self.findById(tranId);
    }

    @SuppressWarnings("unchecked")
    private void upsertTranDtlIfPresent(String tranId, Map<String, Object> canonical) {
        Map<String, Object> section = (Map<String, Object>) canonical.get(CanonicalMappingEngine.SECTION_TRAN_DTL);
        if (section == null) return;
        log.debug("Upserting SEND_TRAN_DTL for tranId={}", tranId);
        dtlRepo.upsert(toChildModel(tranId, section, SendTranDtl.class));
    }

    @SuppressWarnings("unchecked")
    private void upsertRecipDtlIfPresent(String tranId, Map<String, Object> canonical) {
        Map<String, Object> section = (Map<String, Object>) canonical.get(CanonicalMappingEngine.SECTION_RECIP_DTL);
        if (section == null) return;
        log.debug("Upserting SEND_RECIP_DTL for tranId={}", tranId);
        recipRepo.upsert(toChildModel(tranId, section, SendRecipDtl.class));
    }

    /**
     * addrDtl (1:many) semantics:
     * <ul>
     *   <li>{@code null} / absent → addresses untouched</li>
     *   <li>{@code []} → delete all addresses for this tranId</li>
     *   <li>{@code [...]} → MERGE each row by id, then prune ids not in the list</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void upsertAddrDtlIfPresent(String tranId, Map<String, Object> canonical) {
        Object section = canonical.get(CanonicalMappingEngine.SECTION_ADDR_DTL);
        if (!(section instanceof List<?> addrList)) return;
        if (addrList.isEmpty()) {
            log.debug("Clearing all SEND_TRAN_ADDR_DTL for tranId={}", tranId);
            addrRepo.deleteByTranId(tranId);
            return;
        }
        List<SendTranAddrDtl> addresses = toAddrModels(tranId, (List<Map<String, Object>>) addrList);
        log.debug("Merging SEND_TRAN_ADDR_DTL for tranId={}, count={}", tranId, addresses.size());
        addrRepo.mergeAll(addresses);
        List<String> keepIds = addresses.stream().map(SendTranAddrDtl::getId).toList();
        addrRepo.deleteByTranIdNotIn(tranId, keepIds);
    }

    // ── Map → Model conversion ───────────────────────────────

    /**
     * Builds the parent {@link SendTransaction} from the canonical payload.
     * Strips the nested child sections, forces the path-derived {@code tranId},
     * then lets Jackson bind by field name.
     */
    private SendTransaction toParentModel(String tranId, Map<String, Object> canonical) {
        Map<String, Object> map = new HashMap<>(canonical);
        map.put(KEY_TRAN_ID, tranId);
        map.remove(CanonicalMappingEngine.SECTION_TRAN_DTL);
        map.remove(CanonicalMappingEngine.SECTION_RECIP_DTL);
        map.remove(CanonicalMappingEngine.SECTION_ADDR_DTL);
        return objectMapper.convertValue(map, SendTransaction.class);
    }

    /** Generic 1:1 child mapper — injects the path-derived {@code tranId}, binds the rest by field name. */
    private <T> T toChildModel(String tranId, Map<String, Object> source, Class<T> type) {
        Map<String, Object> map = new HashMap<>(source);
        map.put(KEY_TRAN_ID, tranId);
        return objectMapper.convertValue(map, type);
    }

    /**
     * Builds the 1:many address models — preserves a client-supplied {@code id} when
     * present (for updates) and generates a stable UUID for new rows.
     */
    private List<SendTranAddrDtl> toAddrModels(String tranId, List<Map<String, Object>> source) {
        List<SendTranAddrDtl> out = new ArrayList<>(source.size());
        for (Map<String, Object> entry : source) {
            Map<String, Object> map = new HashMap<>(entry);
            Object existingId = map.get("id");
            map.put("id", existingId != null && !existingId.toString().isBlank()
                    ? existingId.toString()
                    : UUID.randomUUID().toString());
            map.put(KEY_TRAN_ID, tranId);
            out.add(objectMapper.convertValue(map, SendTranAddrDtl.class));
        }
        return out;
    }

    // ── Query ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SendTransactionResponse findById(String tranId) {
        log.debug("Fetching full graph for tranId={}", tranId);
        SendTransaction parent = txnRepo.findById(tranId)
                .orElseThrow(() -> new ResourceNotFoundException("SendTransaction", tranId));

        Map<String, Object> clrgSetlmtRow = genericRepo
                .findByPk(CLRG_SETLMT_ALIAS, tranId)
                .orElse(null);

        return toResponse(
                parent,
                dtlRepo.findByTranId(tranId).orElse(null),
                recipRepo.findByTranId(tranId).orElse(null),
                addrRepo.findByTranId(tranId),
                clrgSetlmtRow
        );
    }

    // ── Model → Response (READ path) ─────────────────────────

    /**
     * Copies all scalar fields from {@code p} to a new {@link SendTransactionResponse} by field
     * name, then attaches the child-table responses explicitly. Jackson honours the same
     * camelCase names used by both classes, so any field added to both is wired automatically.
     */
    @SuppressWarnings("unchecked")
    private SendTransactionResponse toResponse(SendTransaction p,
                                               SendTranDtl dtl,
                                               SendRecipDtl recip,
                                               List<SendTranAddrDtl> addrs,
                                               Map<String, Object> clrgSetlmtRow) {
        Map<String, Object> map = objectMapper.convertValue(p, Map.class);
        SendTransactionResponse resp = objectMapper.convertValue(map, SendTransactionResponse.class);
        resp.setTranDtl(dtl != null ? objectMapper.convertValue(dtl, SendTranDtlResponse.class) : null);
        resp.setRecipDtl(recip != null ? objectMapper.convertValue(recip, SendRecipDtlResponse.class) : null);
        resp.setAddrDtl(addrs.stream()
                .map(a -> objectMapper.convertValue(a, SendTranAddrDtlResponse.class))
                .toList());
        if (clrgSetlmtRow != null) {
            resp.setClearing(objectMapper.convertValue(clrgSetlmtRow, SendTranClrgResponse.class));
            resp.setSettlement(objectMapper.convertValue(clrgSetlmtRow, SendTranSetlmtResponse.class));
        }
        return resp;
    }
}
