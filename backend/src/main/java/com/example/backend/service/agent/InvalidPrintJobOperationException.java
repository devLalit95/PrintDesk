package com.example.backend.service.agent;

public class InvalidPrintJobOperationException extends RuntimeException {

    private final boolean conflict;

    public InvalidPrintJobOperationException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
