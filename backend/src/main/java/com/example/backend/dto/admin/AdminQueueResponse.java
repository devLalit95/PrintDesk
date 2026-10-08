package com.example.backend.dto.admin;

import java.util.UUID;

import com.example.backend.entity.PrintJobStatus;

public record AdminQueueResponse(UUID jobId, int attemptNumber, PrintJobStatus status) {
}
