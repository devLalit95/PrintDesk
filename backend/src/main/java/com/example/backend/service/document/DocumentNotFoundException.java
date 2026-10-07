package com.example.backend.service.document;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("The requested document was not found.");
    }
}
