package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Per-source field-mapping overrides declared under the {@code sourceMappings:} key
 * in an event-type YAML.
 *
 * <p>Common mappings (defined at the top-level sections) are applied first.
 * Then the engine looks up the inbound {@code EventEnvelope.eventSource} in the
 * {@code sourceMappings} map (case-insensitive) and, if found, applies these
 * additional / overriding mappings on top of the already-populated DTOs.
 *
 * <p>Source fields that are absent in the JSON produce a null value and are
 * silently skipped — the target field retains its previously mapped value (or null).
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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceMapping {

    private List<FieldMapping> transaction;
    private List<FieldMapping> tranDtl;
    private List<FieldMapping> recipDtl;
    private List<AddrDtlGroup> addrDtl;
    private List<FieldMapping> clrgSetlmt;
}
