package com.example.printagent.printing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LibreOfficeDocxConverter {

    private final String executable;
    private final Duration timeout;

    public LibreOfficeDocxConverter(String executable, Duration timeout) {
        this.executable = executable;
        this.timeout = timeout;
    }

    public Path convertToPdf(Path docxPath, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path isolatedProfile = Files.createTempDirectory(outputDirectory, "office-profile-");
        String profileUri = isolatedProfile.toUri().toString();
        List<String> command = List.of(
                executable,
                "--headless",
                "--nologo",
                "--nodefault",
                "--nolockcheck",
                "--norestore",
                "-env:UserInstallation=" + profileUri,
                "--convert-to",
                "pdf",
                "--outdir",
                outputDirectory.toAbsolutePath().toString(),
                docxPath.toAbsolutePath().toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new PrintExecutionException("DOCX conversion timed out; no document was sent to a printer.");
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new PrintExecutionException("DOCX conversion was interrupted; no document was sent to a printer.");
        }
        if (process.exitValue() != 0) {
            throw new PrintExecutionException("DOCX conversion failed; no document was sent to a printer.");
        }

        String sourceName = docxPath.getFileName().toString();
        String pdfName = sourceName.replaceFirst("(?i)\\.docx$", ".pdf");
        Path pdfPath = outputDirectory.resolve(pdfName);
        if (!Files.isRegularFile(pdfPath) || Files.size(pdfPath) == 0) {
            throw new PrintExecutionException("DOCX conversion produced no usable PDF; no document was sent to a printer.");
        }
        return pdfPath;
    }
}
