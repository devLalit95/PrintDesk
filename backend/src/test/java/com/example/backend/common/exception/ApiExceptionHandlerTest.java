package com.example.backend.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.example.backend.entity.InvalidOrderTransitionException;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.service.admin.InvalidAdminCredentialsException;
import com.example.backend.service.document.DocumentTooLargeException;
import com.example.backend.service.document.InvalidDocumentException;
import com.example.backend.service.order.InvalidPrintOrderRequestException;
import com.example.backend.service.order.OrderTokenGenerationException;
import com.example.backend.service.order.PrintOrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationFailureReturnsProblemDetailsWithoutExposingRejectedValue() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request",
                "copies",
                1000,
                false,
                null,
                null,
                "must be less than or equal to 100"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders");

        ProblemDetail problem = handler.handleValidationFailure(exception, request).getBody();

        assertEquals(400, problem.getStatus());
        assertEquals("Request validation failed", problem.getTitle());
        assertEquals("urn:printdesk:problem:validation-error", problem.getType().toString());
        assertEquals("/api/print-orders", problem.getInstance().toString());
        assertEquals("VALIDATION_ERROR", problem.getProperties().get("code"));
        assertEquals(
                Map.of("copies", "must be less than or equal to 100"),
                problem.getProperties().get("fieldErrors"));
        assertFalse(problem.toString().contains("1000"));
    }

    @Test
    void malformedRequestUsesGenericNonSensitiveMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders");

        ProblemDetail problem = handler.handleUnreadableRequest(null, request).getBody();

        assertEquals(400, problem.getStatus());
        assertEquals("INVALID_REQUEST", problem.getProperties().get("code"));
        assertEquals("The request body is missing or malformed.", problem.getDetail());
        assertFalse(problem.toString().contains("password"));
    }

    @Test
    void oversizedDocumentReturnsPayloadTooLargeProblem() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/documents/upload");

        var response = handler.handleDocumentTooLarge(new DocumentTooLargeException(25_000_000), request);

        assertEquals(413, response.getStatusCode().value());
        assertEquals("DOCUMENT_TOO_LARGE", response.getBody().getProperties().get("code"));
    }

    @Test
    void invalidDocumentReturnsNonSensitiveClientError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/documents/upload");

        var response = handler.handleInvalidDocument(
                new InvalidDocumentException("Only PDF documents are accepted."),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_DOCUMENT", response.getBody().getProperties().get("code"));
        assertEquals("Only PDF documents are accepted.", response.getBody().getDetail());
    }

    @Test
    void invalidPrintOrderReturnsClientError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders");

        var response = handler.handleInvalidPrintOrderRequest(
                new InvalidPrintOrderRequestException("Choose a supported paper size."),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_PRINT_ORDER", response.getBody().getProperties().get("code"));
    }

    @Test
    void unknownPrintOrderReturnsNonDisclosingNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders/UNKNOWN");

        var response = handler.handlePrintOrderNotFound(new PrintOrderNotFoundException(), request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("PRINT_ORDER_NOT_FOUND", response.getBody().getProperties().get("code"));
    }

    @Test
    void invalidStatusTransitionReturnsConflict() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders/123");

        var response = handler.handleInvalidOrderTransition(
                new InvalidOrderTransitionException(PrintOrderStatus.PRINTED, PrintOrderStatus.QUEUED),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("INVALID_ORDER_TRANSITION", response.getBody().getProperties().get("code"));
    }

    @Test
    void tokenGenerationFailureReturnsRetryableServiceUnavailable() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/print-orders");

        var response = handler.handleOrderTokenGenerationFailure(new OrderTokenGenerationException(), request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("ORDER_TOKEN_UNAVAILABLE", response.getBody().getProperties().get("code"));
    }

    @Test
    void invalidAdminCredentialsReturnGenericUnauthorizedProblem() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/login");

        var response = handler.handleInvalidAdminCredentials(
                new InvalidAdminCredentialsException(),
                request);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("INVALID_ADMIN_CREDENTIALS", response.getBody().getProperties().get("code"));
        assertEquals("The username or password is invalid.", response.getBody().getDetail());
    }
}
