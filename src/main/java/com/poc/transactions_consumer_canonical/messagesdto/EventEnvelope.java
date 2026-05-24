package com.poc.transactions_consumer_canonical.messagesdto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Represents the event envelope containing metadata and payload
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class EventEnvelope {

    @NotBlank(message = "regulatoryRegion is mandatory")
    private String regulatoryRegion;

    @NotBlank(message = "eventSource is mandatory")
    private String eventSource;

    @NotBlank(message = "eventName is mandatory")
    private String eventName;

    @NotBlank(message = "eventId is mandatory")
    private String eventId;

    @NotBlank(message = "correlationId is mandatory")
    private String correlationId;

    @Positive(message = "eventTimestamp must be positive")
    private long eventTimestamp;

    @NotBlank(message = "eventMetadata is mandatory")
    private String eventMetadata;

    @NotBlank(message = "eventPayload is mandatory")
    private String eventPayload;

    private boolean ignore;  // Set internally by RuleEngine


}
