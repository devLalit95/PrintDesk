package com.example.backend.service.document;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

@Component
public class PdfPageCounter {

    public int countPages(Path pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw new InvalidDocumentException("The PDF must contain at least one page.");
            }
            return pageCount;
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidDocumentException("The uploaded file is not a readable PDF.", exception);
        }
    }
}
