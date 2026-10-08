package com.example.backend.dto.agent;

import java.util.UUID;

import com.example.backend.entity.PrintJobStatus;
import com.example.backend.entity.PrintType;

public record AgentPrintJobResponse(
        UUID jobId,
        UUID orderId,
        int attemptNumber,
        UUID documentId,
        String contentType,
        String fileName,
        PrintOptions options,
        PrintJobStatus status) {

    public record PrintOptions(
            PrintType printType,
            int copies,
            String paperSize,
            String orientation,
            boolean doubleSided,
            String pageRange) {
    }
}
