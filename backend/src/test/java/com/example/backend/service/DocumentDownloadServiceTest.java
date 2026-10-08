package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.entity.DocumentEntity;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.PrintJobRepository;
import com.example.backend.service.document.DocumentNotFoundException;
import com.example.backend.service.agent.AgentTokenValidator;
import com.example.backend.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class DocumentDownloadServiceTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final byte[] DOCUMENT_CONTENT = "private pdf bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void allowsAdministratorToDownloadDocument() throws Exception {
        DocumentRepository documents = org.mockito.Mockito.mock(DocumentRepository.class);
        PrintJobRepository jobs = org.mockito.Mockito.mock(PrintJobRepository.class);
        DocumentStorage storage = org.mockito.Mockito.mock(DocumentStorage.class);
        AgentTokenValidator agentTokenValidator = org.mockito.Mockito.mock(AgentTokenValidator.class);
        DocumentEntity document = document();
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(storage.open("opaque-storage-key")).thenReturn(new ByteArrayInputStream(DOCUMENT_CONTENT));
        DocumentDownloadService service =
                new DocumentDownloadService(documents, jobs, storage, agentTokenValidator);

        DocumentDownload download = service.download(DOCUMENT_ID, authentication("admin-1", "ROLE_ADMIN"));

        assertEquals("private.pdf", download.fileName());
        assertEquals("application/pdf", download.contentType());
        assertEquals(DOCUMENT_CONTENT.length, download.sizeBytes());
        assertArrayEquals(DOCUMENT_CONTENT, download.content().readAllBytes());
        verify(jobs, never()).existsByPrintOrder_Document_IdAndAgent_AgentCode(DOCUMENT_ID, "admin-1");
    }

    @Test
    void allowsAgentOnlyWhenAssignedToAJobForTheDocument() throws Exception {
        DocumentRepository documents = org.mockito.Mockito.mock(DocumentRepository.class);
        PrintJobRepository jobs = org.mockito.Mockito.mock(PrintJobRepository.class);
        DocumentStorage storage = org.mockito.Mockito.mock(DocumentStorage.class);
        AgentTokenValidator agentTokenValidator = org.mockito.Mockito.mock(AgentTokenValidator.class);
        when(jobs.existsByPrintOrder_Document_IdAndAgent_AgentCode(DOCUMENT_ID, "agent-7")).thenReturn(true);
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document()));
        when(storage.open("opaque-storage-key")).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
        DocumentDownloadService service =
                new DocumentDownloadService(documents, jobs, storage, agentTokenValidator);

        DocumentDownload download = service.download(DOCUMENT_ID, agentAuthentication("agent-7"));

        assertEquals(2, download.content().readAllBytes().length);
        verify(jobs).existsByPrintOrder_Document_IdAndAgent_AgentCode(DOCUMENT_ID, "agent-7");
    }

    @Test
    void doesNotRevealDocumentsToUnassignedAgents() {
        DocumentRepository documents = org.mockito.Mockito.mock(DocumentRepository.class);
        PrintJobRepository jobs = org.mockito.Mockito.mock(PrintJobRepository.class);
        DocumentStorage storage = org.mockito.Mockito.mock(DocumentStorage.class);
        AgentTokenValidator agentTokenValidator = org.mockito.Mockito.mock(AgentTokenValidator.class);
        when(jobs.existsByPrintOrder_Document_IdAndAgent_AgentCode(DOCUMENT_ID, "agent-8")).thenReturn(false);
        DocumentDownloadService service =
                new DocumentDownloadService(documents, jobs, storage, agentTokenValidator);

        assertThrows(
                DocumentNotFoundException.class,
                () -> service.download(DOCUMENT_ID, agentAuthentication("agent-8")));

        verify(documents, never()).findById(DOCUMENT_ID);
    }

    @Test
    void rejectsAuthenticatedCallersWithoutAdminOrAgentRole() {
        DocumentDownloadService service = new DocumentDownloadService(
                org.mockito.Mockito.mock(DocumentRepository.class),
                org.mockito.Mockito.mock(PrintJobRepository.class),
                org.mockito.Mockito.mock(DocumentStorage.class),
                org.mockito.Mockito.mock(AgentTokenValidator.class));

        assertThrows(
                AccessDeniedException.class,
                () -> service.download(DOCUMENT_ID, authentication("customer-1", "ROLE_CUSTOMER")));
    }

    private DocumentEntity document() {
        return new DocumentEntity(
                "private.pdf",
                "opaque-storage-key",
                "application/pdf",
                DOCUMENT_CONTENT.length,
                1,
                "a".repeat(64));
    }

    private UsernamePasswordAuthenticationToken authentication(String name, String role) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "not-used",
                List.of(new SimpleGrantedAuthority(role)));
    }

    private JwtAuthenticationToken agentAuthentication(String agentCode) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(agentCode)
                .claim("agentId", UUID.randomUUID().toString())
                .build();
        return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
    }
}
