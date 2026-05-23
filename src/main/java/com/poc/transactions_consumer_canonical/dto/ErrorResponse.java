package com.poc.transactions_consumer_canonical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    /** ISO-8601 timestamp in UTC — always includes offset so API consumers can parse unambiguously. */
    private OffsetDateTime timestamp;
    private Map<String, String> fieldErrors;
}
