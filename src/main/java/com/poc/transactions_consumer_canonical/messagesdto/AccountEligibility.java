package com.poc.transactions_consumer_canonical.messagesdto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Nested block carried by AIS-style payloads under
 * {@code sendingAccountEligible} / {@code receivingAccountEligible}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class AccountEligibility {

    private Boolean eligible;
    private String responseReasonCode;
    private String responseReasonDetail;
}
