package com.poc.transactions_consumer_canonical.canonicalmapping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One address group inside {@code addrDtl} of an {@link EventTypeMapping}.
 * Each group produces one {@link com.poc.transactions_consumer_canonical.dto.SendTranAddrDtlRequest}
 * row (e.g. addrType=SENDER or addrType=RECIPIENT).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddrDtlGroup {

    /** Value written to {@code SendTranAddrDtlRequest.addrType} (e.g. SENDER, RECIPIENT). */
    private String addrType;

    /** Field-level mappings from {@code TransactionEventAxonMessage} into the address DTO. */
    private List<FieldMapping> mappings;
}
