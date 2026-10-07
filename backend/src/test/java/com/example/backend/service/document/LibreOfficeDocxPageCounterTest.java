package com.example.backend.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibreOfficeDocxPageCounterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void convertsValidDocxAndCountsRenderedPdfPagesWhenLibreOfficeIsInstalled() throws Exception {
        String command = System.getenv().getOrDefault("LIBREOFFICE_COMMAND", "soffice");
        assumeTrue(isAvailable(command), "LibreOffice is not installed in this environment.");
        Path docx = temporaryDirectory.resolve("document.docx");
        createOnePageDocx(docx);
        DocumentProcessingProperties properties =
                new DocumentProcessingProperties(command, 30, 25_000_000, 104_857_600, 10_000);

        int pageCount = new LibreOfficeDocxPageCounter(properties, new PdfPageCounter()).countPages(docx);

        assertEquals(1, pageCount);
    }

    @Test
    void reportsUnavailableWhenTheLibreOfficeExecutableCannotStart() throws Exception {
        Path docx = temporaryDirectory.resolve("document.docx");
        createOnePageDocx(docx);
        DocumentProcessingProperties properties = new DocumentProcessingProperties(
                temporaryDirectory.resolve("missing-libreoffice").toString(),
                30,
                25_000_000,
                104_857_600,
                10_000);
        LibreOfficeDocxPageCounter counter = new LibreOfficeDocxPageCounter(properties, new PdfPageCounter());

        assertThrows(DocumentProcessingUnavailableException.class, () -> counter.countPages(docx));
    }

    private boolean isAvailable(String command) throws Exception {
        try {
            Process process = new ProcessBuilder(List.of(command, "--version"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    private void createOnePageDocx(Path docx) throws Exception {
        try (OutputStream output = Files.newOutputStream(docx);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(
                    zip,
                    "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" "
                            + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/word/document.xml\" "
                            + "ContentType=\"application/vnd.openxmlformats-officedocument."
                            + "wordprocessingml.document.main+xml\"/>"
                            + "</Types>");
            addEntry(
                    zip,
                    "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" "
                            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                            + "Target=\"word/document.xml\"/>"
                            + "</Relationships>");
            addEntry(
                    zip,
                    "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:body><w:p><w:r><w:t>PrintDesk page-count test</w:t></w:r></w:p>"
                            + "<w:sectPr/></w:body></w:document>");
        }
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
