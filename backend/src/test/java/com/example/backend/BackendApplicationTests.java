package com.example.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.backend.dto.order.CreatePrintOrderRequest;
import com.example.backend.dto.order.PriceEstimateRequest;
import com.example.backend.dto.admin.AdminLoginRequest;
import com.example.backend.dto.admin.AdminPrintRequest;
import com.example.backend.dto.admin.AdminUnknownOutcomeRequest;
import com.example.backend.dto.agent.AgentCredentialsRequest;
import com.example.backend.dto.agent.AgentCredentialRotationRequest;
import com.example.backend.dto.agent.AgentClaimRequest;
import com.example.backend.dto.agent.AgentJobEventRequest;
import com.example.backend.dto.agent.AgentHeartbeatRequest;
import com.example.backend.dto.agent.AgentPrinterCapabilitiesRequest;
import com.example.backend.dto.agent.AgentPrinterRegistration;
import com.example.backend.entity.AdminAccountEntity;
import com.example.backend.entity.AdminRole;
import com.example.backend.entity.AuditLogEntity;
import com.example.backend.entity.DocumentEntity;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintRateEntity;
import com.example.backend.entity.PrintType;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.AdminAccountRepository;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.PrintOrderRepository;
import com.example.backend.repository.PrintRateRepository;
import com.example.backend.service.DocumentUploadService;
import com.example.backend.service.PrintOrderService;
import com.example.backend.service.admin.JwtTokenService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import tools.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestPropertySource(properties = {
		"printdesk.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"printdesk.admin.bootstrap.username=test-admin",
		"printdesk.admin.bootstrap.password=test-only-bootstrap-password"
})
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:backend-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
@AutoConfigureMockMvc
class BackendApplicationTests {

	private static final Path STORAGE_ROOT = Path.of(
			System.getProperty("java.io.tmpdir"),
			"printdesk-backend-test-" + UUID.randomUUID());

	@Autowired
	private Flyway flyway;

	@Autowired
	private AdminAccountRepository adminAccountRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private PrintOrderRepository printOrderRepository;

	@Autowired
	private PrintRateRepository printRateRepository;

	@Autowired
	private DocumentUploadService documentUploadService;

	@Autowired
	private PrintOrderService printOrderService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

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
	void bootstrapsAdminOnceAndIssuesThirtyMinuteJwtAccessTokens() throws Exception {
		AdminAccountEntity admin = adminAccountRepository.findByUsername("test-admin").orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(AdminRole.ADMIN, admin.getRole());
		org.junit.jupiter.api.Assertions.assertTrue(
				passwordEncoder.matches("test-only-bootstrap-password", admin.getPasswordHash()));

		var wrongPassword = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminLoginRequest(
								"test-admin",
								"wrong-password"))))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized())
				.andReturn();
		var unknownUser = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminLoginRequest(
								"unknown-admin",
								"wrong-password"))))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized())
				.andReturn();
		var wrongPasswordBody = objectMapper.readTree(wrongPassword.getResponse().getContentAsByteArray());
		var unknownUserBody = objectMapper.readTree(unknownUser.getResponse().getContentAsByteArray());
		org.junit.jupiter.api.Assertions.assertEquals(
				wrongPasswordBody.get("code").stringValue(),
				unknownUserBody.get("code").stringValue());

		MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminLoginRequest(
								" TEST-ADMIN ",
								"test-only-bootstrap-password"))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		var loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray());
		String accessToken = loginBody.get("accessToken").stringValue();
		var jwt = jwtDecoder.decode(accessToken);

		org.junit.jupiter.api.Assertions.assertEquals("Bearer", loginBody.get("tokenType").stringValue());
		org.junit.jupiter.api.Assertions.assertEquals(admin.getId().toString(), jwt.getSubject());
		org.junit.jupiter.api.Assertions.assertEquals(JwtTokenService.DEFAULT_ISSUER, jwt.getIssuer().toString());
		org.junit.jupiter.api.Assertions.assertTrue(
				jwt.getClaimAsStringList("authorities").contains("ROLE_ADMIN"));
		org.junit.jupiter.api.Assertions.assertEquals(
				Duration.ofMinutes(30),
				Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
		org.junit.jupiter.api.Assertions.assertFalse(loginBody.toString().contains("test-only-bootstrap-password"));
		Instant now = Instant.now();
		String wrongIssuerToken = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder()
						.issuer("https://untrusted.example")
						.subject(admin.getId().toString())
						.issuedAt(now)
						.expiresAt(now.plus(Duration.ofMinutes(30)))
						.claim("authorities", java.util.List.of("ROLE_ADMIN"))
						.build())).getTokenValue();
		org.junit.jupiter.api.Assertions.assertThrows(
				JwtException.class,
				() -> jwtDecoder.decode(wrongIssuerToken));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/role-check"))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/role-check")
						.header("Authorization", "Bearer invalid-token"))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/role-check")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(MockMvcResultMatchers.status().isNotFound());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/role-check")
						.with(SecurityMockMvcRequestPostProcessors.user("customer-1").roles("CUSTOMER")))
				.andExpect(MockMvcResultMatchers.status().isForbidden())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void appliesCoreSchemaMigration() {
		org.junit.jupiter.api.Assertions.assertEquals(3, flyway.info().applied().length);
		org.junit.jupiter.api.Assertions.assertTrue(java.util.Arrays.stream(flyway.info().applied())
				.anyMatch(migration -> migration.getVersion().getVersion().equals("2")));
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

	@Test
	void createsOrderWithUniqueSecureTokenAndImmutableRateSnapshot() {
		PrintRateEntity rate = printRateRepository.findAll().stream()
				.filter(existing -> existing.getPrintType() == PrintType.COLOR)
				.findFirst()
				.orElseGet(() -> new PrintRateEntity(PrintType.COLOR, new java.math.BigDecimal("2.50"), "INR"));
		rate.updateRate(new java.math.BigDecimal("2.50"), "INR", true);
		printRateRepository.save(rate);

		DocumentEntity document = documentRepository.save(new DocumentEntity(
				"order-test.pdf",
				"test-" + UUID.randomUUID(),
				"application/pdf",
				42,
				2,
				"b".repeat(64)));

		var created = printOrderService.createOrder(new CreatePrintOrderRequest(
				document.getId(),
				PrintType.COLOR,
				2,
				"Letter",
				"Portrait",
				false));

		org.junit.jupiter.api.Assertions.assertTrue(created.token().matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{12}"));
		PrintOrderEntity persisted = printOrderRepository.findByToken(created.token()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(2, persisted.getPageCount());
		org.junit.jupiter.api.Assertions.assertEquals(4, persisted.getTotalPages());
		org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("2.50"), persisted.getPricePerPage());
		org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("10.00"), persisted.getTotalAmount());
		org.junit.jupiter.api.Assertions.assertEquals("Letter", persisted.getPaperSize());

		rate.updateRate(new java.math.BigDecimal("9.00"), "INR", true);
		printRateRepository.save(rate);
		org.junit.jupiter.api.Assertions.assertEquals(
				new java.math.BigDecimal("2.50"),
				printOrderRepository.findByToken(created.token()).orElseThrow().getPricePerPage());

		printOrderService.transitionStatus(persisted.getId(), PrintOrderStatus.PRINT_REQUESTED);
		printOrderService.transitionStatus(persisted.getId(), PrintOrderStatus.QUEUED);
		printOrderService.transitionStatus(persisted.getId(), PrintOrderStatus.PRINTING);
		printOrderService.transitionStatus(persisted.getId(), PrintOrderStatus.PRINTED);
		var status = printOrderService.getStatusByToken(created.token());
		org.junit.jupiter.api.Assertions.assertEquals(PrintOrderStatus.PRINTED, status.status());
		org.junit.jupiter.api.Assertions.assertNotNull(status.printedAt());
	}

	@Test
	void exposesPublicEstimateOrderAndTokenStatusEndpoints() throws Exception {
		PrintRateEntity rate = printRateRepository.findAll().stream()
				.filter(existing -> existing.getPrintType() == PrintType.COLOR)
				.findFirst()
				.orElseGet(() -> new PrintRateEntity(PrintType.COLOR, new java.math.BigDecimal("2.50"), "INR"));
		rate.updateRate(new java.math.BigDecimal("2.50"), "INR", true);
		printRateRepository.save(rate);

		DocumentEntity document = documentRepository.save(new DocumentEntity(
				"api-order.pdf",
				"api-test-" + UUID.randomUUID(),
				"application/pdf",
				42,
				2,
				"c".repeat(64)));

		MvcResult estimateResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/print-orders/estimate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new PriceEstimateRequest(
								document.getId(),
								PrintType.COLOR,
								2))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		var estimate = objectMapper.readTree(estimateResult.getResponse().getContentAsByteArray());
		org.junit.jupiter.api.Assertions.assertEquals(4, estimate.get("totalPages").intValue());
		org.junit.jupiter.api.Assertions.assertEquals(10.0, estimate.get("totalAmount").doubleValue());

		mockMvc.perform(MockMvcRequestBuilders.post("/api/print-orders/estimate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new PriceEstimateRequest(
								document.getId(),
								PrintType.COLOR,
								0))))
				.andExpect(MockMvcResultMatchers.status().isBadRequest());

		var tamperedOrderPayload = objectMapper.createObjectNode()
				.put("documentId", document.getId().toString())
				.put("printType", PrintType.COLOR.name())
				.put("copies", 2)
				.put("paperSize", "A4")
				.put("orientation", "portrait")
				.put("doubleSided", false)
				.put("pricePerPage", 0.01)
				.put("totalAmount", 0.01);
		MvcResult creationResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/print-orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(tamperedOrderPayload)))
				.andExpect(MockMvcResultMatchers.status().isCreated())
				.andReturn();
		var creation = objectMapper.readTree(creationResult.getResponse().getContentAsByteArray());
		String token = creation.get("token").stringValue();
		org.junit.jupiter.api.Assertions.assertEquals(10.0, creation.get("totalAmount").doubleValue());
		org.junit.jupiter.api.Assertions.assertEquals(
				new java.math.BigDecimal("10.00"),
				printOrderRepository.findByToken(token).orElseThrow().getTotalAmount());
		org.junit.jupiter.api.Assertions.assertEquals(
				"/api/print-orders/" + token,
				creationResult.getResponse().getHeader("Location"));

		MvcResult statusResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/print-orders/{token}", token))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		var status = objectMapper.readTree(statusResult.getResponse().getContentAsByteArray());
		org.junit.jupiter.api.Assertions.assertEquals(token, status.get("token").stringValue());
		org.junit.jupiter.api.Assertions.assertEquals("PENDING", status.get("status").stringValue());
		org.junit.jupiter.api.Assertions.assertEquals(4, status.get("totalPages").intValue());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/print-orders/{token}", "NOT-A-VALID-TOKEN"))
				.andExpect(MockMvcResultMatchers.status().isNotFound());
	}

	@Test
	void exposesProtectedAdminOrderDashboardAndAuditsPendingCancellation() throws Exception {
		var admin = adminAccountRepository.findByUsername("test-admin").orElseThrow();
		var adminJwt = jwt()
				.jwt(token -> token.subject(admin.getId().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
		PrintRateEntity rate = printRateRepository.findAll().stream()
				.filter(existing -> existing.getPrintType() == PrintType.BLACK_AND_WHITE)
				.findFirst()
				.orElseGet(() -> new PrintRateEntity(
						PrintType.BLACK_AND_WHITE,
						new java.math.BigDecimal("1.25"),
						"INR"));
		rate.updateRate(new java.math.BigDecimal("1.25"), "INR", true);
		printRateRepository.save(rate);

		String searchTerm = "admin-dashboard-" + UUID.randomUUID();
		DocumentEntity document = documentRepository.save(new DocumentEntity(
				searchTerm + ".pdf",
				"admin-dashboard-test-" + UUID.randomUUID(),
				"application/pdf",
				42,
				2,
				"d".repeat(64)));
		var order = printOrderService.createOrder(new CreatePrintOrderRequest(
				document.getId(),
				PrintType.BLACK_AND_WHITE,
				1,
				"A4",
				"portrait",
				false));
		UUID orderId = printOrderRepository.findByToken(order.token()).orElseThrow().getId();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard/stats"))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/print-orders")
						.with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
				.andExpect(MockMvcResultMatchers.status().isForbidden());

		MvcResult listResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/print-orders")
						.param("search", searchTerm)
						.param("status", "PENDING")
						.param("page", "0")
						.param("size", "10")
						.with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(1))
				.andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.items[0].status").value("PENDING"))
				.andExpect(MockMvcResultMatchers.jsonPath("$.totalItems").value(1))
				.andReturn();
		org.junit.jupiter.api.Assertions.assertFalse(
				listResult.getResponse().getContentAsString().contains("storage_key"));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/print-orders/{orderId}", orderId)
						.with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.documentId").value(document.getId().toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.attempts").isArray())
				.andExpect(MockMvcResultMatchers.jsonPath("$.attempts.length()").value(0));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard/stats").with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.totalOrders").isNumber())
				.andExpect(MockMvcResultMatchers.jsonPath("$.pendingOrders").isNumber())
				.andExpect(MockMvcResultMatchers.jsonPath("$.printedOrders").isNumber());

		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-orders/{orderId}/cancel", orderId)
						.with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

		org.junit.jupiter.api.Assertions.assertTrue(auditLogRepository.findAll().stream()
				.map(AuditLogEntity::getAction)
				.anyMatch("ORDER_CANCELLED"::equals));

		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-orders/{orderId}/cancel", orderId)
						.with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isConflict())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
		mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/print-orders")
						.param("size", "101")
						.with(adminJwt))
				.andExpect(MockMvcResultMatchers.status().isBadRequest());
	}

	@Test
	void exposesPublicUploadAndRestrictsDocumentDownloads() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
				.andExpect(MockMvcResultMatchers.status().isOk());

		byte[] pdfBytes;
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			pdf.addPage(new PDPage());
			pdf.save(output);
			pdfBytes = output.toByteArray();
		}
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"route-test.pdf",
				"application/pdf",
				pdfBytes);

		MvcResult upload = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/upload").file(file))
				.andExpect(MockMvcResultMatchers.status().isCreated())
				.andReturn();
		var response = objectMapper.readTree(upload.getResponse().getContentAsByteArray());
		String documentId = response.get("documentId").stringValue();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{documentId}", documentId))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized());

		mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{documentId}", documentId)
						.with(SecurityMockMvcRequestPostProcessors.user("customer-1").roles("CUSTOMER")))
				.andExpect(MockMvcResultMatchers.status().isForbidden());

		mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{documentId}", documentId)
						.with(SecurityMockMvcRequestPostProcessors.user("assigned-agent").roles("AGENT")))
				.andExpect(MockMvcResultMatchers.status().isForbidden());

		MvcResult adminDownload = mockMvc.perform(MockMvcRequestBuilders.get(
								"/api/documents/{documentId}", documentId)
						.with(SecurityMockMvcRequestPostProcessors.user("admin-1").roles("ADMIN")))
				.andExpect(MockMvcResultMatchers.request().asyncStarted())
				.andReturn();
		mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(adminDownload))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(MockMvcResultMatchers.content().bytes(pdfBytes));
	}

	@Test
	void provisionsAuthenticatesRotatesRevokesAndHeartbeatsAgents() throws Exception {
		String agentCode = "agent-" + UUID.randomUUID();
		String secret = "agent-secret-value-that-is-at-least-32-bytes";
		String rotatedSecret = "rotated-agent-secret-value-that-is-32-bytes";
		var adminAuth = jwt()
				.jwt(token -> token.subject(UUID.randomUUID().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
		var operatorAuth = jwt()
				.jwt(token -> token.subject(UUID.randomUUID().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));

		mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/agents")
						.with(operatorAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(
								new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isForbidden());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/agents/me/config"))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized());

		MvcResult provision = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/agents")
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(
								new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isCreated())
				.andExpect(MockMvcResultMatchers.jsonPath("$.agentCode").value(agentCode))
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("OFFLINE"))
				.andReturn();
		org.junit.jupiter.api.Assertions.assertFalse(
				provision.getResponse().getContentAsString().contains(secret));

		MvcResult login = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.tokenType").value("Bearer"))
				.andReturn();
		var loginBody = objectMapper.readTree(login.getResponse().getContentAsByteArray());
		String agentToken = loginBody.get("accessToken").stringValue();
		String agentId = loginBody.get("agentId").stringValue();
		org.junit.jupiter.api.Assertions.assertEquals(
				Duration.ofMinutes(5),
				Duration.between(
						jwtDecoder.decode(agentToken).getIssuedAt(),
						jwtDecoder.decode(agentToken).getExpiresAt()));

		AgentHeartbeatRequest heartbeat = new AgentHeartbeatRequest(
				Instant.now(),
				java.util.List.of(new AgentPrinterRegistration(
						"Office_Printer",
						"Office Printer",
						new AgentPrinterCapabilitiesRequest(
								true, true, 50, java.util.List.of("A4", "Letter")))));
		MvcResult heartbeatResponse = mockMvc.perform(MockMvcRequestBuilders.put("/api/agents/me/heartbeat")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(heartbeat)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.agentStatus").value("ONLINE"))
				.andExpect(MockMvcResultMatchers.jsonPath("$.printers[0].capabilities.color").value(true))
				.andReturn();
		String printerId = objectMapper.readTree(heartbeatResponse.getResponse().getContentAsByteArray())
				.get("printers").get(0).get("printerId").stringValue();
		mockMvc.perform(MockMvcRequestBuilders.get("/api/agents/me/config")
						.header("Authorization", "Bearer " + agentToken))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.heartbeatIntervalSeconds").value(30));

		mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/agents/{agentCode}/credential", agentCode)
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentCredentialRotationRequest(rotatedSecret))))
				.andExpect(MockMvcResultMatchers.status().isNoContent());
		mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isUnauthorized())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_AGENT_CREDENTIALS"));

		MvcResult rotatedLogin = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(
								new AgentCredentialsRequest(agentCode, rotatedSecret))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		String rotatedToken = objectMapper.readTree(rotatedLogin.getResponse().getContentAsByteArray())
				.get("accessToken").stringValue();

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/agents/{agentCode}", agentCode)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isNoContent());
		mockMvc.perform(MockMvcRequestBuilders.put("/api/agents/me/heartbeat")
						.header("Authorization", "Bearer " + rotatedToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(heartbeat)))
				.andExpect(MockMvcResultMatchers.status().isForbidden());
		org.junit.jupiter.api.Assertions.assertNotNull(printerId);
		org.junit.jupiter.api.Assertions.assertNotNull(agentId);
	}

	@Test
	void queuesClaimsSeriallyAndRequiresReviewBeforeRetryingUnknownPrints() throws Exception {
		PrintRateEntity rate = printRateRepository.findAll().stream()
				.filter(existing -> existing.getPrintType() == PrintType.BLACK_AND_WHITE)
				.findFirst()
				.orElseGet(() -> new PrintRateEntity(
						PrintType.BLACK_AND_WHITE, new java.math.BigDecimal("1.25"), "INR"));
		rate.updateRate(new java.math.BigDecimal("1.25"), "INR", true);
		printRateRepository.save(rate);

		String agentCode = "queue-agent-" + UUID.randomUUID();
		String secret = "queue-agent-secret-with-at-least-32-bytes";
		var adminAuth = jwt()
				.jwt(token -> token.subject(UUID.randomUUID().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
		var operatorAuth = jwt()
				.jwt(token -> token.subject(UUID.randomUUID().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/agents")
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(
								new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isCreated());
		MvcResult login = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentCredentialsRequest(agentCode, secret))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		String agentToken = objectMapper.readTree(login.getResponse().getContentAsByteArray())
				.get("accessToken").stringValue();
		AgentHeartbeatRequest heartbeat = new AgentHeartbeatRequest(
				Instant.now(),
				java.util.List.of(new AgentPrinterRegistration(
						"Queue_Printer",
						"Queue Printer",
						new AgentPrinterCapabilitiesRequest(
								true, true, 50, java.util.List.of("A4", "Letter")))));
		MvcResult heartbeatResult = mockMvc.perform(MockMvcRequestBuilders.put("/api/agents/me/heartbeat")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(heartbeat)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		String printerId = objectMapper.readTree(heartbeatResult.getResponse().getContentAsByteArray())
				.get("printers").get(0).get("printerId").stringValue();

		UUID firstOrderId = createPendingOrder("queue-first");
		UUID secondOrderId = createPendingOrder("queue-second");
		MvcResult firstQueued = mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-orders/{orderId}/print", firstOrderId)
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminPrintRequest(UUID.fromString(printerId)))))
				.andExpect(MockMvcResultMatchers.status().isAccepted())
				.andReturn();
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-orders/{orderId}/print", secondOrderId)
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminPrintRequest(UUID.fromString(printerId)))))
				.andExpect(MockMvcResultMatchers.status().isAccepted());
		String firstJobId = objectMapper.readTree(firstQueued.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();

		AgentClaimRequest claim = new AgentClaimRequest(UUID.fromString(printerId));
		MvcResult claimed = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.jobId").value(firstJobId))
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CLAIMED"))
				.andReturn();
		mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.jobId").value(firstJobId));
		Instant startTime = Instant.now();
		UUID startEventId = UUID.randomUUID();
		AgentJobEventRequest starting = new AgentJobEventRequest(
				startEventId, com.example.backend.entity.PrintJobStatus.PRINTING, startTime, null, null);
		String claimedJobId = objectMapper.readTree(claimed.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", claimedJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(starting)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PRINTING"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isNoContent());
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", claimedJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(starting)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.duplicate").value(true));
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", claimedJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								startEventId,
								com.example.backend.entity.PrintJobStatus.PRINTING,
								startTime.plusSeconds(1),
								null,
								null))))
				.andExpect(MockMvcResultMatchers.status().isConflict())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", claimedJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								UUID.randomUUID(),
								com.example.backend.entity.PrintJobStatus.PRINTED,
								Instant.now(),
								null,
								null))))
				.andExpect(MockMvcResultMatchers.status().isOk());

		MvcResult secondClaim = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.orderId").value(secondOrderId.toString()))
				.andReturn();
		String secondJobId = objectMapper.readTree(secondClaim.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", secondJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								UUID.randomUUID(),
								com.example.backend.entity.PrintJobStatus.PRINTING,
								Instant.now(),
								null,
								null))))
				.andExpect(MockMvcResultMatchers.status().isOk());
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", secondJobId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								UUID.randomUUID(),
								com.example.backend.entity.PrintJobStatus.OUTCOME_UNKNOWN,
								Instant.now(),
								"OS_ACK_LOST",
								"Printer acknowledgement missing at /tmp/secret-document.pdf"))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("OUTCOME_UNKNOWN"));
		mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/print-jobs/{jobId}/retry", secondJobId)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isConflict());
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-jobs/{jobId}/resolve-unknown", secondJobId)
						.with(operatorAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminUnknownOutcomeRequest(
								AdminUnknownOutcomeRequest.Decision.CONFIRMED_FAILED,
								"Reviewed physical printer"))))
				.andExpect(MockMvcResultMatchers.status().isForbidden());
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-jobs/{jobId}/resolve-unknown", secondJobId)
						.with(adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AdminUnknownOutcomeRequest(
								AdminUnknownOutcomeRequest.Decision.CONFIRMED_FAILED,
								"Reviewed physical printer"))))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.status").value("FAILED"));

		MvcResult retryTwo = mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-jobs/{jobId}/retry", secondJobId)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isAccepted())
				.andExpect(MockMvcResultMatchers.jsonPath("$.attemptNumber").value(2))
				.andReturn();
		String retryTwoJobId = objectMapper.readTree(retryTwo.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		MvcResult retryTwoClaim = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.jobId").value(retryTwoJobId))
				.andReturn();
		String retryTwoClaimedId = objectMapper.readTree(retryTwoClaim.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", retryTwoClaimedId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								UUID.randomUUID(),
								com.example.backend.entity.PrintJobStatus.FAILED,
								Instant.now(),
								"PRINTER_ERROR",
								"Paper jam at /tmp/private.pdf"))))
				.andExpect(MockMvcResultMatchers.status().isOk());
		MvcResult retryThree = mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-jobs/{jobId}/retry", retryTwoClaimedId)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isAccepted())
				.andExpect(MockMvcResultMatchers.jsonPath("$.attemptNumber").value(3))
				.andReturn();
		String retryThreeJobId = objectMapper.readTree(retryThree.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		MvcResult retryThreeClaim = mockMvc.perform(MockMvcRequestBuilders.post("/api/agents/jobs/claim")
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(claim)))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.jobId").value(retryThreeJobId))
				.andReturn();
		String retryThreeClaimedId = objectMapper.readTree(retryThreeClaim.getResponse().getContentAsByteArray())
				.get("jobId").stringValue();
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/agents/jobs/{jobId}/events", retryThreeClaimedId)
						.header("Authorization", "Bearer " + agentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(new AgentJobEventRequest(
								UUID.randomUUID(),
								com.example.backend.entity.PrintJobStatus.FAILED,
								Instant.now(),
								"PRINTER_ERROR",
								"Paper jam"))))
				.andExpect(MockMvcResultMatchers.status().isOk());
		mockMvc.perform(MockMvcRequestBuilders.post(
								"/api/admin/print-jobs/{jobId}/retry", retryThreeClaimedId)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isConflict());

		MvcResult orderDetail = mockMvc.perform(MockMvcRequestBuilders.get(
								"/api/admin/print-orders/{orderId}", secondOrderId)
						.with(adminAuth))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andReturn();
		org.junit.jupiter.api.Assertions.assertFalse(
				objectMapper.readTree(orderDetail.getResponse().getContentAsByteArray()).toString().contains("/tmp/"));
	}

	private UUID createPendingOrder(String fileNamePrefix) {
		DocumentEntity document = documentRepository.save(new DocumentEntity(
				fileNamePrefix + "-" + UUID.randomUUID() + ".pdf",
				fileNamePrefix + "-" + UUID.randomUUID(),
				"application/pdf",
				42,
				1,
				"e".repeat(64)));
		var created = printOrderService.createOrder(new CreatePrintOrderRequest(
				document.getId(),
				PrintType.BLACK_AND_WHITE,
				1,
				"A4",
				"portrait",
				false));
		return printOrderRepository.findByToken(created.token()).orElseThrow().getId();
	}
}
