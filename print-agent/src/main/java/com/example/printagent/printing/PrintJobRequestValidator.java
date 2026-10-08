package com.example.printagent.printing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

public class PrintJobRequestValidator {

    public static final long MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png");
    private static final Set<String> SUPPORTED_PRINT_TYPES = Set.of("BLACK_AND_WHITE", "COLOR");
    private static final Set<String> SUPPORTED_PAPER_SIZES = Set.of("A4", "A3", "Letter", "Legal");
    private static final Set<String> SUPPORTED_ORIENTATIONS = Set.of("portrait", "landscape");
    private static final int MAXIMUM_COPIES = 100;

    public void validate(PrintJobRequest request) {
        if (request == null || request.jobId() == null) {
            throw new PrintJobValidationException("A valid print job identifier is required.");
        }
        Path document = request.documentPath();
        if (document == null || !Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS)) {
            throw new PrintJobValidationException("The downloaded print document is unavailable.");
        }
        try {
            long size = Files.size(document);
            if (size < 1 || size > MAX_DOCUMENT_BYTES) {
                throw new PrintJobValidationException("The print document must be between 1 byte and 25 MB.");
            }
        } catch (IOException exception) {
            throw new PrintJobValidationException("The downloaded print document could not be inspected.");
        }
        if (request.contentType() == null || !SUPPORTED_CONTENT_TYPES.contains(request.contentType())) {
            throw new PrintJobValidationException("The print document format is not supported.");
        }
        if (request.printerSystemName() == null || request.printerSystemName().isBlank()) {
            throw new PrintJobValidationException("A configured printer is required.");
        }
        if (request.printType() == null || !SUPPORTED_PRINT_TYPES.contains(request.printType())) {
            throw new PrintJobValidationException("The requested print type is not supported.");
        }
        if (request.copies() < 1 || request.copies() > MAXIMUM_COPIES) {
            throw new PrintJobValidationException("The copy count must be between 1 and 100.");
        }
        if (request.paperSize() == null || !SUPPORTED_PAPER_SIZES.contains(request.paperSize())) {
            throw new PrintJobValidationException("The requested paper size is not supported.");
        }
        if (request.orientation() == null || !SUPPORTED_ORIENTATIONS.contains(request.orientation())) {
            throw new PrintJobValidationException("The requested page orientation is not supported.");
        }
    }
}
