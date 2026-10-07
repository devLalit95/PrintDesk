package com.example.backend.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.example.backend.dto.document.DocumentUploadResponse;
import com.example.backend.service.DocumentDownload;
import com.example.backend.service.DocumentDownloadService;
import com.example.backend.service.DocumentUploadService;
import com.example.backend.service.document.DocumentStorageException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService uploadService;
    private final DocumentDownloadService downloadService;

    public DocumentController(
            DocumentUploadService uploadService,
            DocumentDownloadService downloadService) {
        this.uploadService = uploadService;
        this.downloadService = downloadService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        DocumentUploadResponse response = uploadService.upload(file);
        return ResponseEntity.created(URI.create("/api/documents/" + response.documentId()))
                .body(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable UUID documentId,
            Authentication authentication) {
        DocumentDownload download = downloadService.download(documentId, authentication);
        StreamingResponseBody body = output -> {
            try (InputStream content = download.content()) {
                content.transferTo(output);
            } catch (IOException exception) {
                throw new DocumentStorageException("The stored document could not be streamed.", exception);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
