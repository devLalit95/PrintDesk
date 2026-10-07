package com.example.backend.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentContentInspectorTest {

    @TempDir
    Path temporaryDirectory;

    private final DocumentProcessingProperties properties =
            new DocumentProcessingProperties("soffice", 30, 25_000_000, 104_857_600, 10_000);

    @Test
    void inspectsPdfAndCountsPages() throws Exception {
        Path pdf = temporaryDirectory.resolve("upload.bin");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        DocumentInspection inspection = inspector().inspect(pdf);

        assertEquals("application/pdf", inspection.contentType());
        assertEquals(2, inspection.pageCount());
    }

    @Test
    void rejectsCorruptPdf() throws Exception {
        Path pdf = temporaryDirectory.resolve("broken.pdf");
        Files.writeString(pdf, "%PDF-not-a-valid-document");

        assertThrows(InvalidDocumentException.class, () -> inspector().inspect(pdf));
    }

    @Test
    void validatesPngAndCountsItAsOnePage() throws Exception {
        Path png = temporaryDirectory.resolve("upload.bin");
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());

        DocumentInspection inspection = inspector().inspect(png);

        assertEquals("image/png", inspection.contentType());
        assertEquals(1, inspection.pageCount());
    }

    @Test
    void validatesJpegAndCountsItAsOnePage() throws Exception {
        Path jpeg = temporaryDirectory.resolve("upload.bin");
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg.toFile());

        DocumentInspection inspection = inspector().inspect(jpeg);

        assertEquals("image/jpeg", inspection.contentType());
        assertEquals(1, inspection.pageCount());
    }

    @Test
    void rejectsImagesAboveThePixelLimit() throws Exception {
        Path png = temporaryDirectory.resolve("large.png");
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());
        DocumentProcessingProperties limitedProperties =
                new DocumentProcessingProperties("soffice", 30, 11, 104_857_600, 10_000);
        DocumentContentInspector inspector =
                new DocumentContentInspector(limitedProperties, new PdfPageCounter(), ignored -> 1);

        assertThrows(InvalidDocumentException.class, () -> inspector.inspect(png));
    }

    @Test
    void validatesDocxPackageBeforeCountingConvertedPages() throws Exception {
        Path docx = temporaryDirectory.resolve("upload.docx");
        createDocx(docx);
        DocumentContentInspector inspector = new DocumentContentInspector(
                properties,
                new PdfPageCounter(),
                ignored -> 3);

        DocumentInspection inspection = inspector.inspect(docx);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                inspection.contentType());
        assertEquals(3, inspection.pageCount());
    }

    @Test
    void rejectsNonDocxZipContent() throws Exception {
        Path zip = temporaryDirectory.resolve("not-a-docx.zip");
        try (OutputStream output = Files.newOutputStream(zip);
                ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry("readme.txt"));
            zipOutput.write("not a document".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }

        assertThrows(InvalidDocumentException.class, () -> inspector().inspect(zip));
    }

    @Test
    void rejectsUnsupportedContent() throws Exception {
        Path text = temporaryDirectory.resolve("upload.txt");
        Files.writeString(text, "not an accepted document");

        assertThrows(InvalidDocumentException.class, () -> inspector().inspect(text));
    }

    private DocumentContentInspector inspector() {
        return new DocumentContentInspector(properties, new PdfPageCounter(), ignored -> 1);
    }

    private void createDocx(Path docx) throws Exception {
        try (OutputStream output = Files.newOutputStream(docx);
                ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            addZipEntry(zipOutput, "[Content_Types].xml", "<Types/>");
            addZipEntry(zipOutput, "word/document.xml", "<document/>");
        }
    }

    private void addZipEntry(ZipOutputStream output, String name, String value) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
