package com.poc.transactions_consumer_canonical.exception;

import com.poc.transactions_consumer_canonical.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleNotFoundReturnsTraceable404() {
        MDC.put("traceId", "trace-1");

        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("transaction", "missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).extracting(ErrorResponse::getError,
                ErrorResponse::getMessage, ErrorResponse::getTraceId)
                .containsExactly("Not Found", "transaction not found with id: missing", "trace-1");
    }

    @Test
    void handleMethodArgumentValidationCollectsFirstFieldErrorAndDefaultMessage() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "amount", "must be positive"));
        binding.addError(new FieldError("request", "amount", "duplicate ignored"));
        binding.addError(new FieldError("request", "currency", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getMessage()).isEqualTo("One or more fields are invalid");
        assertThat(response.getBody().getFieldErrors())
                .containsEntry("amount", "must be positive")
                .containsEntry("currency", "Invalid value");
    }

    @Test
    void handleConstraintViolationCollectsPropertyPaths() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("pageSize");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        ResponseEntity<ErrorResponse> response =
                handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("One or more request parameters are invalid");
        assertThat(response.getBody().getFieldErrors())
                .containsEntry("pageSize", "must be less than or equal to 100");
    }

    @Test
    void handleMetadataValidationMirrorsValidationShape() {
        MetadataValidationException ex =
                new MetadataValidationException(Map.of("tranId", "is required"));

        ResponseEntity<ErrorResponse> response = handler.handleMetadataValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getFieldErrors()).containsEntry("tranId", "is required");
        assertThat(ex.getMessage()).contains("Metadata validation failed");
    }

    @Test
    void handleDataAccessMasksDatabaseDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataAccess(new DataRetrievalFailureException("ORA-00942 table missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("Database Error");
        assertThat(response.getBody().getMessage()).doesNotContain("ORA-00942");
    }

    @Test
    void handleGenericMasksInternalDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new IllegalStateException("stack trace details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMessage()).doesNotContain("stack trace details");
    }
}
