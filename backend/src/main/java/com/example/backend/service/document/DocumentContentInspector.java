package com.example.backend.service.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

@Component
public class DocumentContentInspector {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final byte[] JPEG_SIGNATURE = {
        (byte) 0xff, (byte) 0xd8, (byte) 0xff
    };
    private static final int PREFIX_LENGTH = 8;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final DocumentProcessingProperties properties;
    private final PdfPageCounter pdfPageCounter;
    private final DocxPageCounter docxPageCounter;

    public DocumentContentInspector(
            DocumentProcessingProperties properties,
            PdfPageCounter pdfPageCounter,
            DocxPageCounter docxPageCounter) {
        this.properties = properties;
        this.pdfPageCounter = pdfPageCounter;
        this.docxPageCounter = docxPageCounter;
    }

    public DocumentInspection inspect(Path document) {
        byte[] prefix = readPrefix(document);
        if (startsWith(prefix, PDF_SIGNATURE)) {
            return new DocumentInspection(PDF_CONTENT_TYPE, pdfPageCounter.countPages(document));
        }
        if (startsWith(prefix, PNG_SIGNATURE)) {
            return new DocumentInspection(PNG_CONTENT_TYPE, inspectImage(document, "png"));
        }
        if (startsWith(prefix, JPEG_SIGNATURE)) {
            return new DocumentInspection(JPEG_CONTENT_TYPE, inspectImage(document, "jpeg"));
        }
        if (isZipSignature(prefix)) {
            validateDocxPackage(document);
            int pageCount = docxPageCounter.countPages(document);
            if (pageCount < 1) {
                throw new InvalidDocumentException("The DOCX must contain at least one page.");
            }
            return new DocumentInspection(DOCX_CONTENT_TYPE, pageCount);
        }
        throw unsupportedDocument();
    }

    private byte[] readPrefix(Path document) {
        byte[] prefix = new byte[PREFIX_LENGTH];
        try (InputStream input = Files.newInputStream(document)) {
            int bytesRead = input.read(prefix);
            if (bytesRead < 1) {
                throw unsupportedDocument();
            }
            return bytesRead == prefix.length ? prefix : java.util.Arrays.copyOf(prefix, bytesRead);
        } catch (IOException exception) {
            throw new InvalidDocumentException("The uploaded file could not be read.", exception);
        }
    }

    private int inspectImage(Path document, String expectedFormat) {
        try (ImageInputStream input = ImageIO.createImageInputStream(document.toFile())) {
            if (input == null) {
                throw new InvalidDocumentException("The uploaded image is not readable.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidDocumentException("The uploaded image is corrupt or unsupported.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName().equalsIgnoreCase(expectedFormat)) {
                    throw new InvalidDocumentException("The image signature does not match its encoded format.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || (long) width * height > properties.maxImagePixels()) {
                    throw new InvalidDocumentException(
                            "The image dimensions exceed the supported processing limit.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidDocumentException("The uploaded image is corrupt or unsupported.");
                }
                return 1;
            } finally {
                reader.dispose();
            }
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidDocumentException("The uploaded image is corrupt or unsupported.", exception);
        }
    }

    private void validateDocxPackage(Path document) {
        Set<String> names = new HashSet<>();
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        long expandedBytes = 0;
        int entryCount = 0;

        try (ZipFile zip = new ZipFile(document.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                String name = entry.getName();
                if (entryCount > properties.maxDocxEntries()
                        || name.startsWith("/")
                        || name.contains("\\")
                        || java.util.Arrays.asList(name.split("/")).contains("..")
                        || !names.add(name)) {
                    throw unsupportedDocx();
                }
                hasContentTypes |= name.equals("[Content_Types].xml");
                hasWordDocument |= name.equals("word/document.xml");

                if (!entry.isDirectory()) {
                    try (InputStream content = zip.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = content.read(buffer)) != -1) {
                            if (bytesRead > properties.maxDocxExpandedBytes() - expandedBytes) {
                                throw new InvalidDocumentException(
                                        "The DOCX expands beyond the supported processing limit.");
                            }
                            expandedBytes += bytesRead;
                        }
                    }
                }
            }
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidDocumentException("The uploaded file is not a valid DOCX document.", exception);
        }

        if (!hasContentTypes || !hasWordDocument) {
            throw unsupportedDocx();
        }
    }

    private boolean isZipSignature(byte[] prefix) {
        return prefix.length >= 4
                && prefix[0] == 'P'
                && prefix[1] == 'K'
                && ((prefix[2] == 3 && prefix[3] == 4)
                        || (prefix[2] == 5 && prefix[3] == 6)
                        || (prefix[2] == 7 && prefix[3] == 8));
    }

    private boolean startsWith(byte[] value, byte[] signature) {
        if (value.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (value[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private InvalidDocumentException unsupportedDocx() {
        return new InvalidDocumentException("The uploaded file is not a valid DOCX document.");
    }

    private InvalidDocumentException unsupportedDocument() {
        return new InvalidDocumentException("Only PDF, DOCX, JPG, and PNG documents are accepted.");
    }
}
