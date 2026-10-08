package com.example.printagent.printing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrintJobRequestValidatorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsSupportedBackendValidatedDocumentAndSettings() throws IOException {
        Path document = Files.writeString(tempDirectory.resolve("document.pdf"), "payload");
        PrintJobRequest request = new PrintJobRequest(
                UUID.randomUUID(),
                document,
                "application/pdf",
                "Configured Printer",
                "COLOR",
                2,
                "Letter",
                "landscape",
                true);

        assertDoesNotThrow(() -> new PrintJobRequestValidator().validate(request));
    }

    @Test
    void rejectsMissingDocumentsUnsupportedTypesAndInvalidCopyCounts() throws IOException {
        PrintJobRequestValidator validator = new PrintJobRequestValidator();
        Path document = Files.writeString(tempDirectory.resolve("document.pdf"), "payload");

        assertThrows(PrintJobValidationException.class, () -> validator.validate(null));
        assertThrows(PrintJobValidationException.class, () -> validator.validate(new PrintJobRequest(
                UUID.randomUUID(), tempDirectory.resolve("missing.pdf"), "application/pdf",
                "Printer", "COLOR", 1, "A4", "portrait", false)));
        assertThrows(PrintJobValidationException.class, () -> validator.validate(new PrintJobRequest(
                UUID.randomUUID(), document, "application/x-executable",
                "Printer", "COLOR", 1, "A4", "portrait", false)));
        assertThrows(PrintJobValidationException.class, () -> validator.validate(new PrintJobRequest(
                UUID.randomUUID(), document, "application/pdf",
                "Printer", "COLOR", 0, "A4", "portrait", false)));
        assertThrows(PrintJobValidationException.class, () -> validator.validate(new PrintJobRequest(
                UUID.randomUUID(), document, null,
                "Printer", "COLOR", 1, "A4", "portrait", false)));
    }

    @Test
    void rejectsSymbolicLinksToDocuments() throws IOException {
        Path actual = Files.writeString(tempDirectory.resolve("actual.pdf"), "payload");
        Path link = tempDirectory.resolve("linked.pdf");
        try {
            Files.createSymbolicLink(link, actual);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.abort("The test environment does not permit creation of a symbolic link.");
        }
        PrintJobRequest request = new PrintJobRequest(
                UUID.randomUUID(), link, "application/pdf",
                "Printer", "COLOR", 1, "A4", "portrait", false);

        assertThrows(PrintJobValidationException.class, () -> new PrintJobRequestValidator().validate(request));
    }
}
