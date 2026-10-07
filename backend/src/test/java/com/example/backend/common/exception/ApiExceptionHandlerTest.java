package com.example.backend.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

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
}
