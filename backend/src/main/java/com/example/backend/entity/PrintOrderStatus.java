package com.example.backend.entity;

public enum PrintOrderStatus {
    PENDING,
    PRINT_REQUESTED,
    QUEUED,
    PRINTING,
    PRINTED,
    FAILED,
    OUTCOME_UNKNOWN,
    CANCELLED
}
