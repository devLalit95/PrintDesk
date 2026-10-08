package com.example.printagent.printing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class DefaultPrintDocumentPreparer implements PrintDocumentPreparer {

    private final Path workDirectory;
    private final LibreOfficeDocxConverter docxConverter;

    public DefaultPrintDocumentPreparer(
            Path workDirectory,
            String libreOfficeCommand,
            Duration conversionTimeout) {
        this.workDirectory = workDirectory;
        this.docxConverter = new LibreOfficeDocxConverter(libreOfficeCommand, conversionTimeout);
    }

    @Override
    public PrintDocument prepare(PrintJobRequest request) throws IOException {
        return switch (request.contentType()) {
            case "application/pdf" -> new PdfPrintDocument(request.documentPath(), request.printType());
            case "image/jpeg", "image/png" -> new ImagePrintDocument(request.documentPath());
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    prepareDocx(request);
            default -> throw new PrintJobValidationException("The print document format is not supported.");
        };
    }

    private PrintDocument prepareDocx(PrintJobRequest request) throws IOException {
        Files.createDirectories(workDirectory);
        Path conversionDirectory = Files.createTempDirectory(workDirectory, "docx-print-");
        try {
            Path convertedPdf = docxConverter.convertToPdf(request.documentPath(), conversionDirectory);
            return new TemporaryPdfPrintDocument(
                    new PdfPrintDocument(convertedPdf, request.printType()),
                    conversionDirectory);
        } catch (IOException | RuntimeException exception) {
            deleteDirectory(conversionDirectory);
            throw exception;
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class TemporaryPdfPrintDocument implements PrintDocument {

        private final PrintDocument delegate;
        private final Path temporaryDirectory;

        private TemporaryPdfPrintDocument(PrintDocument delegate, Path temporaryDirectory) {
            this.delegate = delegate;
            this.temporaryDirectory = temporaryDirectory;
        }

        @Override
        public java.awt.print.Printable printable() {
            return delegate.printable();
        }

        @Override
        public int pageCount() {
            return delegate.pageCount();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                try (var paths = Files.walk(temporaryDirectory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }
}
