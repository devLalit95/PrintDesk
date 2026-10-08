package com.example.backend.service;

import java.io.IOException;
import java.util.UUID;

import com.example.backend.entity.DocumentEntity;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.PrintJobRepository;
import com.example.backend.service.agent.AgentTokenValidator;
import com.example.backend.service.document.DocumentNotFoundException;
import com.example.backend.service.document.DocumentStorageException;
import com.example.backend.storage.DocumentStorage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class DocumentDownloadService {

    private final DocumentRepository documentRepository;
    private final PrintJobRepository printJobRepository;
    private final DocumentStorage documentStorage;
    private final AgentTokenValidator agentTokenValidator;

    public DocumentDownloadService(
            DocumentRepository documentRepository,
            PrintJobRepository printJobRepository,
            DocumentStorage documentStorage,
            AgentTokenValidator agentTokenValidator) {
        this.documentRepository = documentRepository;
        this.printJobRepository = printJobRepository;
        this.documentStorage = documentStorage;
        this.agentTokenValidator = agentTokenValidator;
    }

    public DocumentDownload download(UUID documentId, Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication is required to download documents.");
        }

        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            return openDocument(documentId);
        }
        if (hasAuthority(authentication, "ROLE_AGENT")) {
            if (!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt token)) {
                throw new AccessDeniedException("The agent token is invalid.");
            }
            agentTokenValidator.requireActiveAgent(token);
            if (!printJobRepository.existsByPrintOrder_Document_IdAndAgent_AgentCode(
                    documentId,
                    authentication.getName())) {
                throw new DocumentNotFoundException();
            }
            return openDocument(documentId);
        }
        throw new AccessDeniedException("The caller is not authorized to download documents.");
    }

    private DocumentDownload openDocument(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(DocumentNotFoundException::new);
        try {
            return new DocumentDownload(
                    document.getOriginalFileName(),
                    document.getContentType(),
                    document.getSizeBytes(),
                    documentStorage.open(document.getStorageKey()));
        } catch (IOException exception) {
            throw new DocumentStorageException("The stored document could not be opened.", exception);
        }
    }

    private boolean hasAuthority(Authentication authentication, String requiredAuthority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredAuthority::equals);
    }
}
