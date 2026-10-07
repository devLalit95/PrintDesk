package com.example.backend.entity;

public enum PrintJobStatus {
    QUEUED,
    CLAIMED,
    PRINTING,
    PRINTED,
    FAILED,
    CANCELLED,
    OUTCOME_UNKNOWN
}
