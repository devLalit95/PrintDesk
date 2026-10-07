package com.example.backend.common.exception;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.example.backend.entity.InvalidOrderTransitionException;
import com.example.backend.service.document.DocumentProcessingUnavailableException;
import com.example.backend.service.document.DocumentNotFoundException;
import com.example.backend.service.document.DocumentStorageException;
import com.example.backend.service.document.DocumentTooLargeException;
import com.example.backend.service.document.InvalidDocumentException;
import com.example.backend.service.admin.InvalidAdminCredentialsException;
import com.example.backend.service.admin.InvalidAdminOrderQueryException;
import com.example.backend.service.order.InvalidPrintOrderRequestException;
import com.example.backend.service.order.OrderTokenGenerationException;
import com.example.backend.service.order.PrintOrderNotFoundException;
import com.example.backend.service.pricing.InvalidPricingRequestException;
import com.example.backend.service.pricing.PricingUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid.",
                "urn:printdesk:problem:validation-error",
                "VALIDATION_ERROR",
                request);
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()));

        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request values are invalid.",
                "urn:printdesk:problem:validation-error",
                "VALIDATION_ERROR",
                request);
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request body is missing or malformed.",
                "urn:printdesk:problem:invalid-request",
                "INVALID_REQUEST",
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDocument(
            InvalidDocumentException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid document",
                exception.getMessage(),
                "urn:printdesk:problem:invalid-document",
                "INVALID_DOCUMENT",
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleDocumentTooLarge(
            DocumentTooLargeException exception,
            HttpServletRequest request) {
        HttpStatusCode status = HttpStatusCode.valueOf(413);
        ProblemDetail problem = createProblem(
                status,
                "Document too large",
                exception.getMessage(),
                "urn:printdesk:problem:document-too-large",
                "DOCUMENT_TOO_LARGE",
                request);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(DocumentProcessingUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleDocumentProcessingUnavailable(
            DocumentProcessingUnavailableException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Document processing unavailable",
                "The document could not be processed right now. Please retry later.",
                "urn:printdesk:problem:document-processing-unavailable",
                "DOCUMENT_PROCESSING_UNAVAILABLE",
                request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<ProblemDetail> handleDocumentStorageFailure(
            DocumentStorageException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Document storage failed",
                "The document could not be stored. Please retry later.",
                "urn:printdesk:problem:document-storage-failure",
                "DOCUMENT_STORAGE_FAILURE",
                request);
        return ResponseEntity.internalServerError().body(problem);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDocumentNotFound(
            DocumentNotFoundException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.NOT_FOUND,
                "Document not found",
                exception.getMessage(),
                "urn:printdesk:problem:document-not-found",
                "DOCUMENT_NOT_FOUND",
                request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InvalidPricingRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPricingRequest(
            InvalidPricingRequestException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid pricing request",
                exception.getMessage(),
                "urn:printdesk:problem:invalid-pricing-request",
                "INVALID_PRICING_REQUEST",
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(PricingUnavailableException.class)
    public ResponseEntity<ProblemDetail> handlePricingUnavailable(
            PricingUnavailableException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Pricing unavailable",
                exception.getMessage(),
                "urn:printdesk:problem:pricing-unavailable",
                "PRICING_UNAVAILABLE",
                request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(InvalidPrintOrderRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPrintOrderRequest(
            InvalidPrintOrderRequestException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid print order",
                exception.getMessage(),
                "urn:printdesk:problem:invalid-print-order",
                "INVALID_PRINT_ORDER",
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(PrintOrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePrintOrderNotFound(
            PrintOrderNotFoundException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.NOT_FOUND,
                "Print order not found",
                exception.getMessage(),
                "urn:printdesk:problem:print-order-not-found",
                "PRINT_ORDER_NOT_FOUND",
                request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderTransition(
            InvalidOrderTransitionException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.CONFLICT,
                "Invalid print order transition",
                exception.getMessage(),
                "urn:printdesk:problem:invalid-order-transition",
                "INVALID_ORDER_TRANSITION",
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentOrderUpdate(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.CONFLICT,
                "Order changed",
                "The order was changed by another request. Refresh it and try again.",
                "urn:printdesk:problem:concurrent-order-update",
                "CONCURRENT_ORDER_UPDATE",
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OrderTokenGenerationException.class)
    public ResponseEntity<ProblemDetail> handleOrderTokenGenerationFailure(
            OrderTokenGenerationException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Order token unavailable",
                "A unique order token could not be generated. Please retry later.",
                "urn:printdesk:problem:order-token-unavailable",
                "ORDER_TOKEN_UNAVAILABLE",
                request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(InvalidAdminCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAdminCredentials(
            InvalidAdminCredentialsException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                "The username or password is invalid.",
                "urn:printdesk:problem:invalid-admin-credentials",
                "INVALID_ADMIN_CREDENTIALS",
                request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(InvalidAdminOrderQueryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAdminOrderQuery(
            InvalidAdminOrderQueryException exception,
            HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid admin order query",
                exception.getMessage(),
                "urn:printdesk:problem:invalid-admin-order-query",
                "INVALID_ADMIN_ORDER_QUERY",
                request);
        return ResponseEntity.badRequest().body(problem);
    }

    private ProblemDetail createProblem(
            HttpStatusCode status,
            String title,
            String detail,
            String type,
            String code,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
