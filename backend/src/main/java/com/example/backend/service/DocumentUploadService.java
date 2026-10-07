package com.example.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;

import com.example.backend.dto.document.DocumentUploadResponse;
import com.example.backend.entity.DocumentEntity;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.service.document.DocumentContentInspector;
import com.example.backend.service.document.DocumentInspection;
import com.example.backend.service.document.DocumentProcessingUnavailableException;
import com.example.backend.service.document.DocumentStorageException;
import com.example.backend.service.document.DocumentTooLargeException;
import com.example.backend.service.document.InvalidDocumentException;
import com.example.backend.storage.DocumentStorage;
import com.example.backend.storage.DocumentStorageProperties;
import com.example.backend.storage.StorageLimitExceededException;
import com.example.backend.storage.StoredDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final DocumentStorage documentStorage;
    private final DocumentRepository documentRepository;
    private final DocumentContentInspector contentInspector;
    private final long maxFileBytes;

    public DocumentUploadService(
            DocumentStorage documentStorage,
            DocumentRepository documentRepository,
            DocumentContentInspector contentInspector,
            DocumentStorageProperties storageProperties) {
        this.documentStorage = documentStorage;
        this.documentRepository = documentRepository;
        this.contentInspector = contentInspector;
        this.maxFileBytes = storageProperties.maxFileSize().toBytes();
    }

    public DocumentUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("A non-empty document file is required.");
        }
        if (file.getSize() > maxFileBytes) {
            throw new DocumentTooLargeException(maxFileBytes);
        }
        String originalFileName = sanitizeFileName(file.getOriginalFilename());

        StoredDocument storedDocument = null;
        try {
            try (InputStream content = file.getInputStream()) {
                storedDocument = documentStorage.store(content, maxFileBytes);
            } catch (StorageLimitExceededException exception) {
                throw new DocumentTooLargeException(maxFileBytes);
            }

            DocumentInspection inspection = inspectStoredDocument(storedDocument);
            DocumentEntity document = new DocumentEntity(
                    originalFileName,
                    storedDocument.storageKey(),
                    inspection.contentType(),
                    storedDocument.sizeBytes(),
                    inspection.pageCount(),
                    storedDocument.sha256Hex());
            DocumentEntity savedDocument = documentRepository.saveAndFlush(document);
            return new DocumentUploadResponse(
                    savedDocument.getId(),
                    originalFileName,
                    inspection.contentType(),
                    storedDocument.sizeBytes(),
                    inspection.pageCount());
        } catch (InvalidDocumentException | DocumentProcessingUnavailableException exception) {
            cleanupStoredDocument(storedDocument, exception);
            throw exception;
        } catch (DocumentTooLargeException exception) {
            cleanupStoredDocument(storedDocument, exception);
            throw exception;
        } catch (IOException exception) {
            DocumentStorageException storageException =
                    new DocumentStorageException("The uploaded document could not be stored safely.", exception);
            cleanupStoredDocument(storedDocument, storageException);
            throw storageException;
        } catch (RuntimeException exception) {
            cleanupStoredDocument(storedDocument, exception);
            throw exception;
        }
    }

    private DocumentInspection inspectStoredDocument(StoredDocument storedDocument) throws IOException {
        Path processingDirectory = Files.createTempDirectory("printdesk-upload-");
        Path processingFile = processingDirectory.resolve("document.docx");
        Exception failure = null;
        try {
            try (InputStream storedContent = documentStorage.open(storedDocument.storageKey())) {
                Files.copy(storedContent, processingFile);
            }
            return contentInspector.inspect(processingFile);
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            deleteProcessingFiles(processingFile, processingDirectory, failure);
        }
    }

    private void deleteProcessingFiles(Path processingFile, Path processingDirectory, Exception failure)
            throws IOException {
        IOException cleanupFailure = null;
        try {
            Files.deleteIfExists(processingFile);
        } catch (IOException exception) {
            cleanupFailure = exception;
        }
        try {
            Files.deleteIfExists(processingDirectory);
        } catch (IOException exception) {
            if (cleanupFailure == null) {
                cleanupFailure = exception;
            } else {
                cleanupFailure.addSuppressed(exception);
            }
        }
        if (cleanupFailure != null) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            } else {
                throw cleanupFailure;
            }
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidDocumentException("A document file name is required.");
        }
        String normalized = Normalizer.normalize(originalFileName, Normalizer.Form.NFC).replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        StringBuilder safeName = new StringBuilder();
        basename.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(255)
                .forEach(safeName::appendCodePoint);
        String result = safeName.toString().trim();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) {
            throw new InvalidDocumentException("A valid document file name is required.");
        }
        return result;
    }

    private void cleanupStoredDocument(StoredDocument storedDocument, RuntimeException failure) {
        if (storedDocument == null) {
            return;
        }
        try {
            documentStorage.delete(storedDocument.storageKey());
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }
}
