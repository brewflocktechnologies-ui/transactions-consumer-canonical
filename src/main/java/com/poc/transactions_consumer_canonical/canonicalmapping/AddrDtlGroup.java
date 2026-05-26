package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One address group inside {@code addrDtl} of an {@link EventTypeMapping}.
 * Each group produces one entry in the {@code addrDtl} list of the canonical
 * payload (a {@code Map<String,Object>} keyed by {@code jsonName} from
 * {@code metadata/send_tran_addr_dtl.yaml}), e.g. {@code addrType=SENDER}
 * or {@code addrType=RECIPIENT}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddrDtlGroup {

    /** Address-type discriminator written to {@code addrType} on the row (e.g. SENDER, RECIPIENT). */
    private String addrType;

    /** Field-level mappings from the source JSON payload into this address entry. */
    private List<FieldMapping> mappings;
}
