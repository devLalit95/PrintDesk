package com.example.printagent.printing;

public record PrintOutcome(Status status, String message) {

    public enum Status {
        OS_JOB_COMPLETED,
        FAILED,
        OUTCOME_UNKNOWN
    }
}
