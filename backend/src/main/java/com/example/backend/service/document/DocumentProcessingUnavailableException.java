package com.example.backend.service.document;

public class DocumentProcessingUnavailableException extends RuntimeException {

    public DocumentProcessingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
