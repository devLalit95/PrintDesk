package com.example.backend.service.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class LibreOfficeDocxPageCounter implements DocxPageCounter {

    private final DocumentProcessingProperties properties;
    private final PdfPageCounter pdfPageCounter;

    public LibreOfficeDocxPageCounter(
            DocumentProcessingProperties properties,
            PdfPageCounter pdfPageCounter) {
        this.properties = properties;
        this.pdfPageCounter = pdfPageCounter;
    }

    @Override
    public int countPages(Path docxFile) {
        Path workingDirectory = null;
        RuntimeException failure = null;
        try {
            workingDirectory = Files.createTempDirectory("printdesk-docx-");
            Path outputDirectory = Files.createDirectory(workingDirectory.resolve("output"));
            Path userProfile = Files.createDirectory(workingDirectory.resolve("profile"));
            Path convertedPdf = outputDirectory.resolve("document.pdf");
            List<String> command = List.of(
                    properties.libreOfficeCommand(),
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "-env:UserInstallation=" + userProfile.toUri().toASCIIString(),
                    "--convert-to",
                    "pdf:writer_pdf_Export",
                    "--outdir",
                    outputDirectory.toString(),
                    docxFile.toString());

            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException exception) {
                throw new DocumentProcessingUnavailableException(
                        "DOCX page counting is unavailable because LibreOffice could not be started.",
                        exception);
            }

            try {
                process.getOutputStream().close();
                if (!process.waitFor(
                        Duration.ofSeconds(properties.docxConversionTimeoutSeconds()).toMillis(),
                        TimeUnit.MILLISECONDS)) {
                    stopProcess(process);
                    throw new DocumentProcessingUnavailableException(
                            "DOCX page counting timed out. Try a smaller or simpler document.",
                            new IOException("LibreOffice conversion exceeded its configured time limit."));
                }
            } catch (InterruptedException exception) {
                stopProcess(process);
                Thread.currentThread().interrupt();
                throw new DocumentProcessingUnavailableException(
                        "DOCX page counting was interrupted.",
                        exception);
            } catch (IOException exception) {
                stopProcess(process);
                throw new DocumentProcessingUnavailableException(
                        "DOCX page counting could not communicate with LibreOffice.",
                        exception);
            }

            if (process.exitValue() != 0 || !Files.isRegularFile(convertedPdf)) {
                throw new InvalidDocumentException(
                        "LibreOffice could not convert the DOCX. Check that the document opens correctly and retry.");
            }
            if (Files.size(convertedPdf) > properties.maxDocxExpandedBytes()) {
                throw new InvalidDocumentException("The converted DOCX exceeds the permitted processing limit.");
            }
            return pdfPageCounter.countPages(convertedPdf);
        } catch (DocumentProcessingUnavailableException | InvalidDocumentException exception) {
            failure = exception;
            throw exception;
        } catch (IOException exception) {
            DocumentProcessingUnavailableException unavailable = new DocumentProcessingUnavailableException(
                    "DOCX page counting could not complete.",
                    exception);
            failure = unavailable;
            throw unavailable;
        } finally {
            if (workingDirectory != null) {
                try {
                    deleteTree(workingDirectory);
                } catch (IOException cleanupException) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupException);
                    } else {
                        throw new DocumentProcessingUnavailableException(
                                "Temporary DOCX conversion data could not be removed.",
                                cleanupException);
                    }
                }
            }
        }
    }

    private void stopProcess(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
