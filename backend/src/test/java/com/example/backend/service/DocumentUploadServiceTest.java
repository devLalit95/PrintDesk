package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.example.backend.entity.DocumentEntity;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.service.document.DocumentContentInspector;
import com.example.backend.service.document.DocumentInspection;
import com.example.backend.service.document.InvalidDocumentException;
import com.example.backend.storage.DocumentStorageProperties;
import com.example.backend.storage.LocalDocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class DocumentUploadServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesStoresAndPersistsDetectedMetadata() throws Exception {
        DocumentRepository repository = org.mockito.Mockito.mock(DocumentRepository.class);
        DocumentContentInspector inspector = org.mockito.Mockito.mock(DocumentContentInspector.class);
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private-storage"));
        DocumentUploadService service = new DocumentUploadService(
                storage,
                repository,
                inspector,
                new DocumentStorageProperties(temporaryDirectory.resolve("private-storage"), DataSize.ofMegabytes(25)));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../customer-report.pdf",
                "text/plain",
                new byte[] {1, 2, 3, 4});
        when(inspector.inspect(any(Path.class)))
                .thenReturn(new DocumentInspection("application/pdf", 2));
        when(repository.saveAndFlush(any(DocumentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(file);

        assertEquals("customer-report.pdf", response.fileName());
        assertEquals("application/pdf", response.contentType());
        assertEquals(4, response.sizeBytes());
        assertEquals(2, response.pageCount());
        verify(repository).saveAndFlush(any(DocumentEntity.class));
        try (Stream<Path> paths = Files.walk(temporaryDirectory.resolve("private-storage"))) {
            assertEquals(1, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void removesStoredBytesWhenInspectionRejectsTheFile() throws Exception {
        DocumentRepository repository = org.mockito.Mockito.mock(DocumentRepository.class);
        DocumentContentInspector inspector = org.mockito.Mockito.mock(DocumentContentInspector.class);
        Path storageRoot = temporaryDirectory.resolve("private-storage");
        LocalDocumentStorage storage = new LocalDocumentStorage(storageRoot);
        DocumentUploadService service = new DocumentUploadService(
                storage,
                repository,
                inspector,
                new DocumentStorageProperties(storageRoot, DataSize.ofMegabytes(25)));
        when(inspector.inspect(any(Path.class)))
                .thenThrow(new InvalidDocumentException("Unsupported content."));

        assertThrows(
                InvalidDocumentException.class,
                () -> service.upload(new MockMultipartFile(
                        "file",
                        "document.pdf",
                        "application/pdf",
                        new byte[] {1, 2, 3})));

        verify(repository, never()).saveAndFlush(any(DocumentEntity.class));
        try (Stream<Path> paths = Files.walk(storageRoot)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void rejectsEmptyUploadBeforeTouchingStorageOrRepository() {
        DocumentRepository repository = org.mockito.Mockito.mock(DocumentRepository.class);
        DocumentContentInspector inspector = org.mockito.Mockito.mock(DocumentContentInspector.class);
        LocalDocumentStorage storage = new LocalDocumentStorage(temporaryDirectory.resolve("private-storage"));
        DocumentUploadService service = new DocumentUploadService(
                storage,
                repository,
                inspector,
                new DocumentStorageProperties(temporaryDirectory.resolve("private-storage"), DataSize.ofMegabytes(25)));

        assertThrows(
                InvalidDocumentException.class,
                () -> service.upload(new MockMultipartFile(
                        "file",
                        "empty.pdf",
                        "application/pdf",
                        new byte[0])));

        verify(repository, never()).saveAndFlush(any(DocumentEntity.class));
        verify(inspector, never()).inspect(any(Path.class));
    }
}
