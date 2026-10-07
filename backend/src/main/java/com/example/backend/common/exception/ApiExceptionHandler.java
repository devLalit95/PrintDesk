package com.example.backend.common.exception;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.example.backend.service.document.DocumentProcessingUnavailableException;
import com.example.backend.service.document.DocumentStorageException;
import com.example.backend.service.document.DocumentTooLargeException;
import com.example.backend.service.document.InvalidDocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
