package com.example.printagent.printing;

import java.nio.file.Path;
import java.util.UUID;

public record PrintJobRequest(
        UUID jobId,
        Path documentPath,
        String contentType,
        String printerSystemName,
        String printType,
        int copies,
        String paperSize,
        String orientation,
        boolean doubleSided) {
}
