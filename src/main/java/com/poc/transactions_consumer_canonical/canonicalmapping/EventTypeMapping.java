package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Root model for one canonical-mapping YAML file (e.g. PAYMENT.yaml, FUNDING.yaml).
 *
 * <pre>
 * eventType:   PAYMENT
 * eventNames:
 *   - TRANSACTION_INITIATED
 *   - PAYMENT_COMPLETED
 * tranIdSource: tranId          # field on TransactionEventAxonMessage used as DB TRAN_ID
 * tranType:     SEND            # literal value for SendTransactionRequest.tranType
 * transaction:  [ {source,target}, … ]
 * tranDtl:      [ {source,target}, … ]
 * recipDtl:     [ {source,target}, … ]
 * addrDtl:
 *   - addrType: SENDER
 *     mappings: [ {source,target}, … ]
 *   - addrType: RECIPIENT
 *     mappings: [ {source,target}, … ]
 * </pre>
 *
 * To add a new event type: drop a new YAML file under
 * {@code classpath:canonical-mappings/} — no Java changes needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTypeMapping {

    /**
     * Unique identifier for this mapping (also used as fallback match key when
     * {@code eventNames} is empty).
     */
    private String eventType;

    /**
     * List of {@code EventEnvelope.eventName} values routed to this mapping.
     * If absent/empty the registry falls back to matching {@code eventType} literally.
     */
    private List<String> eventNames;

    /**
     * Field name on {@code TransactionEventAxonMessage} whose value becomes the
     * DB {@code TRAN_ID}.  Falls back to {@code EventEnvelope.correlationId} when
     * the field is null or blank.
     */
    private String tranIdSource;

    /** Literal value written to {@code SendTransactionRequest.tranType}. */
    private String tranType;

    /** Mappings into {@code SendTransactionRequest} top-level fields. */
    private List<FieldMapping> transaction;

    /** Mappings into {@code SendTranDtlRequest} (1:1 child). */
    private List<FieldMapping> tranDtl;

    /** Mappings into {@code SendRecipDtlRequest} (1:1 child). */
    private List<FieldMapping> recipDtl;

    /**
     * Address groups for {@code SendTranAddrDtlRequest} (1:many child).
     * Each group produces one address row.
     */
    private List<AddrDtlGroup> addrDtl;

    /**
     * Mappings into {@code SendTranClrgSetlmtRequest} (1:1 child, 5th table).
     * Used by CLEARING and SETTLEMENT events; the standard PAYMENT/FUNDING flows
     * leave this null.
     */
    private List<FieldMapping> clrgSetlmt;

    /**
     * Optional pipeline selector.  When set to {@code "CLRG_SETLMT"} the consumer
     * routes the event to the 5th-table ({@code SEND_TRAN_CLRG_SETLMT}) path via
     * {@code ClearingEventService} instead of the standard 4-table
     * {@code SendTransactionService} path.  Absent or {@code null} = standard path.
     *
     * <p>Adding a new 5th-table event type only requires a new YAML file with
     * {@code pipeline: CLRG_SETLMT} — no Java changes.
     */
    private String pipeline;

    /**
     * Optional filtering rules evaluated before the mapping pipeline runs.
     * A {@code null} rules block means "allow all messages" for this event type.
     *
     * <pre>
     * rules:
     *   allowedEventSources:
     *     - AIS_SERVICE
     *   allowedOperations:
     *     - A
     *     - U
     * </pre>
     */
    private RulesConfig rules;

    /**
     * Per-source mapping overrides applied on top of the common sections above.
     * The map key is the {@code EventEnvelope.eventSource} value (case-insensitive lookup).
     * Common mappings run first; source-specific mappings overlay / supplement them.
     *
     * <pre>
     * sourceMappings:
     *   SEND_COMMON_SERVICES:
     *     transaction:
     *       - { source: network,    target: ntwrkCd }
     *   AIS_SERVICE:
     *     transaction:
     *       - { source: networkSrc, target: ntwrkCd }
     * </pre>
     */
    private Map<String, SourceMapping> sourceMappings;
}
