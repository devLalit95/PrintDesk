package com.example.backend.service.document;

public class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException(long maxBytes) {
        super("The uploaded document exceeds the maximum allowed size of " + maxBytes + " bytes.");
    }
}
