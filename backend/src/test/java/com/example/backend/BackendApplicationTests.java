package com.example.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.backend.repository.DocumentRepository;
import com.example.backend.service.DocumentUploadService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.flywaydb.core.Flyway;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:backend-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
class BackendApplicationTests {

	private static final Path STORAGE_ROOT = Path.of(
			System.getProperty("java.io.tmpdir"),
			"printdesk-backend-test-" + UUID.randomUUID());

	@Autowired
	private Flyway flyway;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private DocumentUploadService documentUploadService;

	@DynamicPropertySource
	static void configureStorageRoot(DynamicPropertyRegistry registry) {
		registry.add("printdesk.storage.root", () -> STORAGE_ROOT.toString());
	}

	@AfterAll
	static void cleanTestStorage() throws IOException {
		if (Files.exists(STORAGE_ROOT)) {
			try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	void appliesCoreSchemaMigration() {
		org.junit.jupiter.api.Assertions.assertEquals(1, flyway.info().applied().length);
	}

	@Test
	void uploadsPdfAndPersistsValidatedMetadata() throws Exception {
		byte[] pdfBytes;
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			pdf.addPage(new PDPage());
			pdf.save(output);
			pdfBytes = output.toByteArray();
		}

		var response = documentUploadService.upload(new MockMultipartFile(
				"file",
				"invoice.pdf",
				"application/pdf",
				pdfBytes));

		var persisted = documentRepository.findById(response.documentId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals("application/pdf", persisted.getContentType());
		org.junit.jupiter.api.Assertions.assertEquals("invoice.pdf", persisted.getOriginalFileName());
		org.junit.jupiter.api.Assertions.assertEquals(1, persisted.getPageCount());
		org.junit.jupiter.api.Assertions.assertEquals(pdfBytes.length, persisted.getSizeBytes());
		org.junit.jupiter.api.Assertions.assertEquals(64, persisted.getSha256Hex().length());
	}
}
