package com.poc.transactions_consumer_canonical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.transactions_consumer_canonical.dto.*;
import com.poc.transactions_consumer_canonical.exception.ResourceNotFoundException;
import com.poc.transactions_consumer_canonical.model.*;
import com.poc.transactions_consumer_canonical.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendTransactionServiceImpl implements SendTransactionService {

    private final SendTransactionRepository txnRepo;
    private final SendTranDtlRepository dtlRepo;
    private final SendRecipDtlRepository recipRepo;
    private final SendTranAddrDtlRepository addrRepo;
    /** Used by {@link #toParentModel} and {@link #toResponse} for field-name-based copying. */
    private final ObjectMapper objectMapper;

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
    public SendTransactionResponse upsert(String tranId, SendTransactionRequest req) {
        log.info("Upserting transaction: tranId={}, tranType={}, curStat={}", tranId, req.getTranType(), req.getCurStat());

        // 1. Upsert parent
        log.debug("Upserting SEND_TRANSACTIONS for tranId={}", tranId);
        txnRepo.upsert(toParentModel(tranId, req));

        // 2. Upsert SEND_TRAN_DTL (1:1) — only if provided
        if (req.getTranDtl() != null) {
            log.debug("Upserting SEND_TRAN_DTL for tranId={}", tranId);
            dtlRepo.upsert(toDtlModel(tranId, req.getTranDtl()));
        }

        // 3. Upsert SEND_RECIP_DTL (1:1) — only if provided
        if (req.getRecipDtl() != null) {
            log.debug("Upserting SEND_RECIP_DTL for tranId={}", tranId);
            recipRepo.upsert(toRecipModel(tranId, req.getRecipDtl()));
        }

        // 4. Merge SEND_TRAN_ADDR_DTL (1:many) — only if list is non-null.
        //    null  → leave existing addresses untouched.
        //    []    → delete all addresses.
        //    [...] → merge each by ID (null guard applied per field), remove IDs not in list.
        if (req.getAddrDtl() != null) {
            if (req.getAddrDtl().isEmpty()) {
                log.debug("Clearing all SEND_TRAN_ADDR_DTL for tranId={}", tranId);
                addrRepo.deleteByTranId(tranId);
            } else {
                List<SendTranAddrDtl> addresses = toAddrModels(tranId, req.getAddrDtl());
                log.debug("Merging SEND_TRAN_ADDR_DTL for tranId={}, count={}", tranId, addresses.size());
                addrRepo.mergeAll(addresses);
                List<String> keepIds = addresses.stream().map(SendTranAddrDtl::getId).toList();
                addrRepo.deleteByTranIdNotIn(tranId, keepIds);
            }
        }

        log.info("Upsert complete for tranId={}", tranId);
        return self.findById(tranId);
    }

    // ── Query ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SendTransactionResponse findById(String tranId) {
        log.debug("Fetching full graph for tranId={}", tranId);
        SendTransaction parent = txnRepo.findById(tranId)
                .orElseThrow(() -> new ResourceNotFoundException("SendTransaction", tranId));

        return toResponse(
                parent,
                dtlRepo.findByTranId(tranId).orElse(null),
                recipRepo.findByTranId(tranId).orElse(null),
                addrRepo.findByTranId(tranId)
        );
    }

    // ── Mappers : Request → Model ─────────────────────────────

    /**
     * Copies all scalar fields from {@code r} to a new {@link SendTransaction} by field name.
     * Jackson's {@code convertValue} honours the same camelCase names used by both classes,
     * so any field added to {@link SendTransactionRequest} <em>and</em> {@link SendTransaction}
     * is wired automatically — no manual getter/setter call needed here.
     *
     * <p>{@code tranId} is injected separately because it lives in the URL path, not the body.
     * Child-table entries ({@code tranDtl}, {@code recipDtl}, {@code addrDtl}) are present in
     * the request map but absent from {@link SendTransaction}; Jackson silently ignores them
     * ({@code FAIL_ON_UNKNOWN_PROPERTIES = false} is the Spring Boot default).
     */
    @SuppressWarnings("unchecked")
    private SendTransaction toParentModel(String tranId, SendTransactionRequest r) {
        Map<String, Object> map = objectMapper.convertValue(r, Map.class);
        map.put("tranId", tranId);
        // Remove child-table keys that exist on SendTransactionRequest but not on
        // SendTransaction.  This ensures the subsequent convertValue succeeds
        // regardless of whether FAIL_ON_UNKNOWN_PROPERTIES is enabled on the mapper.
        map.remove("tranDtl");
        map.remove("recipDtl");
        map.remove("addrDtl");
        return objectMapper.convertValue(map, SendTransaction.class);
    }

    private SendTranDtl toDtlModel(String tranId, SendTranDtlRequest r) {
        return SendTranDtl.builder()
                .tranId(tranId)
                .paymtRef(r.getPaymtRef())
                .unqTranRef(r.getUnqTranRef())
                .acqCntryNam(r.getAcqCntryNam())
                .acqIca(r.getAcqIca())
                .fundSrc(r.getFundSrc())
                .ichgRateDsgn(r.getIchgRateDsgn())
                .merchCatCd(r.getMerchCatCd())
                .paymtType(r.getPaymtType())
                .pointServIntrctn(r.getPointServIntrctn())
                .tranPrps(r.getTranPrps())
                .tranSetlAmt(r.getTranSetlAmt())
                .bncGtwyRqst(r.getBncGtwyRqst())
                .bncGtwyResp(r.getBncGtwyResp())
                .origRqstPyld(r.getOrigRqstPyld())
                .origRespPyld(r.getOrigRespPyld())
                .tranfrAcptId(r.getTranfrAcptId())
                .tranfrAcptNam(r.getTranfrAcptNam())
                .mcAssgnMerch(r.getMcAssgnMerch())
                .paymtFacltrId(r.getPaymtFacltrId())
                .subMerchId(r.getSubMerchId())
                .tranfrTrmlId(r.getTranfrTrmlId())
                .tranfrAcptStLine1(r.getTranfrAcptStLine1())
                .tranfrAcptStLine2(r.getTranfrAcptStLine2())
                .tranfrAcptCity(r.getTranfrAcptCity())
                .tranfrAcptSt(r.getTranfrAcptSt())
                .tranfrAcptCntryNam(r.getTranfrAcptCntryNam())
                .tranfrAcptPostCd(r.getTranfrAcptPostCd())
                .crteUserNam(r.getCrteUserNam())
                .updtUserNam(r.getUpdtUserNam())
                .eventId(r.getEventId())
                .eventTs(r.getEventTs())
                .eventCorltnId(r.getEventCorltnId())
                .msgVersion(r.getMsgVersion())
                .tranCrteDt(r.getTranCrteDt())
                .tranTypeIndCd(r.getTranTypeIndCd())
                .regulatedRateTypeCd(r.getRegulatedRateTypeCd())
                .cvcRespDesc(r.getCvcRespDesc())
                .procId(r.getProcId())
                .acqIdenCd(r.getAcqIdenCd())
                .tranfrAcptMpgId(r.getTranfrAcptMpgId())
                .tranfrAcptMerchValue(r.getTranfrAcptMerchValue())
                .build();
    }

    private SendRecipDtl toRecipModel(String tranId, SendRecipDtlRequest r) {
        return SendRecipDtl.builder()
                .tranId(tranId)
                .sendFirstNam(r.getSendFirstNam())
                .sendMidNam(r.getSendMidNam())
                .sendLstNam(r.getSendLstNam())
                .sendPhn(r.getSendPhn())
                .sendEmail(r.getSendEmail())
                .sendDob(r.getSendDob())
                .sendNatl(r.getSendNatl())
                .sendBirthCntryNam(r.getSendBirthCntryNam())
                .sendAcctNum(r.getSendAcctNum())
                .sendAcctUri(r.getSendAcctUri())
                .sendGovtIdUri(r.getSendGovtIdUri())
                .sendAcctNumType(r.getSendAcctNumType())
                .sendCardNum(r.getSendCardNum())
                .sendCardExpirDt(r.getSendCardExpirDt())
                .sendStLine1(r.getSendStLine1())
                .sendStLine2(r.getSendStLine2())
                .sendCity(r.getSendCity())
                .sendSt(r.getSendSt())
                .sendCntryNam(r.getSendCntryNam())
                .sendPostCd(r.getSendPostCd())
                .recipFirstNam(r.getRecipFirstNam())
                .recipMidNam(r.getRecipMidNam())
                .recipLstNam(r.getRecipLstNam())
                .recipPhn(r.getRecipPhn())
                .recipEmail(r.getRecipEmail())
                .recipDob(r.getRecipDob())
                .recipNatl(r.getRecipNatl())
                .recipBirthCntryNam(r.getRecipBirthCntryNam())
                .recipAcctNum(r.getRecipAcctNum())
                .recipAcctUri(r.getRecipAcctUri())
                .recipGovtIdUri(r.getRecipGovtIdUri())
                .recipAcctNumType(r.getRecipAcctNumType())
                .recipCardNum(r.getRecipCardNum())
                .recipCardExpirDt(r.getRecipCardExpirDt())
                .recipStLine1(r.getRecipStLine1())
                .recipStLine2(r.getRecipStLine2())
                .recipCity(r.getRecipCity())
                .recipSt(r.getRecipSt())
                .recipCntryNam(r.getRecipCntryNam())
                .recipPostCd(r.getRecipPostCd())
                .crteUserNam(r.getCrteUserNam())
                .updtUserNam(r.getUpdtUserNam())
                .tranCrteDt(r.getTranCrteDt())
                .build();
    }

    private List<SendTranAddrDtl> toAddrModels(String tranId, List<SendTranAddrDtlRequest> list) {
        return list.stream().map(r -> SendTranAddrDtl.builder()
                // Use client-supplied ID for updates; generate a stable UUID for new records.
                .id(r.getId() != null && !r.getId().isBlank() ? r.getId() : UUID.randomUUID().toString())
                .tranId(tranId)
                .addrType(r.getAddrType())
                .stLine1(r.getStLine1())
                .stLine2(r.getStLine2())
                .city(r.getCity())
                .st(r.getSt())
                .cntryNam(r.getCntryNam())
                .postCd(r.getPostCd())
                .addrStat(r.getAddrStat())
                .postCdStat(r.getPostCdStat())
                .crteUserNam(r.getCrteUserNam())
                .updtUserNam(r.getUpdtUserNam())
                .build()).toList();
    }

    // ── Mappers : Model → Response ────────────────────────────

    /**
     * Copies all scalar fields from {@code p} to a new {@link SendTransactionResponse} by field
     * name, then attaches the child-table responses explicitly.
     *
     * <p>Because both {@link SendTransaction} and {@link SendTransactionResponse} share the same
     * camelCase field names for every parent-table column, any field added to both classes is
     * surfaced in the response automatically — no manual getter/setter call needed here.
     *
     * <p>Child-table fields ({@code tranDtl}, {@code recipDtl}, {@code addrDtl}) are not on
     * {@link SendTransaction}, so they will not appear in the intermediate map; they are wired
     * explicitly after the Jackson conversion.
     */
    @SuppressWarnings("unchecked")
    private SendTransactionResponse toResponse(SendTransaction p,
                                               SendTranDtl dtl,
                                               SendRecipDtl recip,
                                               List<SendTranAddrDtl> addrs) {
        Map<String, Object> map = objectMapper.convertValue(p, Map.class);
        SendTransactionResponse resp = objectMapper.convertValue(map, SendTransactionResponse.class);
        resp.setTranDtl(dtl != null ? toDtlResponse(dtl) : null);
        resp.setRecipDtl(recip != null ? toRecipResponse(recip) : null);
        resp.setAddrDtl(addrs.stream().map(this::toAddrResponse).toList());
        return resp;
    }

    private SendTranDtlResponse toDtlResponse(SendTranDtl d) {
        return SendTranDtlResponse.builder()
                .tranId(d.getTranId())
                .paymtRef(d.getPaymtRef())
                .unqTranRef(d.getUnqTranRef())
                .acqCntryNam(d.getAcqCntryNam())
                .acqIca(d.getAcqIca())
                .fundSrc(d.getFundSrc())
                .ichgRateDsgn(d.getIchgRateDsgn())
                .merchCatCd(d.getMerchCatCd())
                .paymtType(d.getPaymtType())
                .pointServIntrctn(d.getPointServIntrctn())
                .tranPrps(d.getTranPrps())
                .tranSetlAmt(d.getTranSetlAmt())
                .bncGtwyRqst(d.getBncGtwyRqst())
                .bncGtwyResp(d.getBncGtwyResp())
                .origRqstPyld(d.getOrigRqstPyld())
                .origRespPyld(d.getOrigRespPyld())
                .tranfrAcptId(d.getTranfrAcptId())
                .tranfrAcptNam(d.getTranfrAcptNam())
                .mcAssgnMerch(d.getMcAssgnMerch())
                .paymtFacltrId(d.getPaymtFacltrId())
                .subMerchId(d.getSubMerchId())
                .tranfrTrmlId(d.getTranfrTrmlId())
                .tranfrAcptStLine1(d.getTranfrAcptStLine1())
                .tranfrAcptStLine2(d.getTranfrAcptStLine2())
                .tranfrAcptCity(d.getTranfrAcptCity())
                .tranfrAcptSt(d.getTranfrAcptSt())
                .tranfrAcptCntryNam(d.getTranfrAcptCntryNam())
                .tranfrAcptPostCd(d.getTranfrAcptPostCd())
                .crteTs(d.getCrteTs())
                .crteUserNam(d.getCrteUserNam())
                .updtTs(d.getUpdtTs())
                .updtUserNam(d.getUpdtUserNam())
                .eventId(d.getEventId())
                .eventTs(d.getEventTs())
                .eventCorltnId(d.getEventCorltnId())
                .msgVersion(d.getMsgVersion())
                .tranCrteDt(d.getTranCrteDt())
                .rplctnUpdtTs(d.getRplctnUpdtTs())
                .tranTypeIndCd(d.getTranTypeIndCd())
                .regulatedRateTypeCd(d.getRegulatedRateTypeCd())
                .cvcRespDesc(d.getCvcRespDesc())
                .procId(d.getProcId())
                .acqIdenCd(d.getAcqIdenCd())
                .tranfrAcptMpgId(d.getTranfrAcptMpgId())
                .tranfrAcptMerchValue(d.getTranfrAcptMerchValue())
                .build();
    }

    private SendRecipDtlResponse toRecipResponse(SendRecipDtl r) {
        return SendRecipDtlResponse.builder()
                .tranId(r.getTranId())
                .sendFirstNam(r.getSendFirstNam())
                .sendMidNam(r.getSendMidNam())
                .sendLstNam(r.getSendLstNam())
                .sendPhn(r.getSendPhn())
                .sendEmail(r.getSendEmail())
                .sendDob(r.getSendDob())
                .sendNatl(r.getSendNatl())
                .sendBirthCntryNam(r.getSendBirthCntryNam())
                .sendAcctNum(r.getSendAcctNum())
                .sendAcctUri(r.getSendAcctUri())
                .sendGovtIdUri(r.getSendGovtIdUri())
                .sendAcctNumType(r.getSendAcctNumType())
                .sendCardNum(r.getSendCardNum())
                .sendCardExpirDt(r.getSendCardExpirDt())
                .sendStLine1(r.getSendStLine1())
                .sendStLine2(r.getSendStLine2())
                .sendCity(r.getSendCity())
                .sendSt(r.getSendSt())
                .sendCntryNam(r.getSendCntryNam())
                .sendPostCd(r.getSendPostCd())
                .recipFirstNam(r.getRecipFirstNam())
                .recipMidNam(r.getRecipMidNam())
                .recipLstNam(r.getRecipLstNam())
                .recipPhn(r.getRecipPhn())
                .recipEmail(r.getRecipEmail())
                .recipDob(r.getRecipDob())
                .recipNatl(r.getRecipNatl())
                .recipBirthCntryNam(r.getRecipBirthCntryNam())
                .recipAcctNum(r.getRecipAcctNum())
                .recipAcctUri(r.getRecipAcctUri())
                .recipGovtIdUri(r.getRecipGovtIdUri())
                .recipAcctNumType(r.getRecipAcctNumType())
                .recipCardNum(r.getRecipCardNum())
                .recipCardExpirDt(r.getRecipCardExpirDt())
                .recipStLine1(r.getRecipStLine1())
                .recipStLine2(r.getRecipStLine2())
                .recipCity(r.getRecipCity())
                .recipSt(r.getRecipSt())
                .recipCntryNam(r.getRecipCntryNam())
                .recipPostCd(r.getRecipPostCd())
                .crteTs(r.getCrteTs())
                .crteUserNam(r.getCrteUserNam())
                .updtTs(r.getUpdtTs())
                .updtUserNam(r.getUpdtUserNam())
                .tranCrteDt(r.getTranCrteDt())
                .rplctnUpdtTs(r.getRplctnUpdtTs())
                .build();
    }

    private SendTranAddrDtlResponse toAddrResponse(SendTranAddrDtl a) {
        return SendTranAddrDtlResponse.builder()
                .id(a.getId())
                .tranId(a.getTranId())
                .addrType(a.getAddrType())
                .stLine1(a.getStLine1())
                .stLine2(a.getStLine2())
                .city(a.getCity())
                .st(a.getSt())
                .cntryNam(a.getCntryNam())
                .postCd(a.getPostCd())
                .addrStat(a.getAddrStat())
                .postCdStat(a.getPostCdStat())
                .crteTs(a.getCrteTs())
                .updtTs(a.getUpdtTs())
                .crteUserNam(a.getCrteUserNam())
                .updtUserNam(a.getUpdtUserNam())
                .rplctnUpdtTs(a.getRplctnUpdtTs())
                .build();
    }
}
