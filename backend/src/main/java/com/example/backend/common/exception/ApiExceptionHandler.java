package com.example.backend.common.exception;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
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

    private ProblemDetail createProblem(
            HttpStatus status,
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
