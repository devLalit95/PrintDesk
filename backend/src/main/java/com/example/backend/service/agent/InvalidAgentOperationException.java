package com.example.backend.service.agent;

public class InvalidAgentOperationException extends RuntimeException {

    private final boolean conflict;

    public InvalidAgentOperationException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
