package com.example.backend.dto.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class UploadDocumentRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresAnUploadedFile() {
        Set<ConstraintViolation<UploadDocumentRequest>> violations =
                validator.validate(new UploadDocumentRequest(null));

        assertEquals(1, violations.size());
        assertEquals("file", violations.iterator().next().getPropertyPath().toString());
        assertEquals("A document file is required.", violations.iterator().next().getMessage());
    }

    @Test
    void acceptsAProvidedFile() {
        Set<ConstraintViolation<UploadDocumentRequest>> violations =
                validator.validate(new UploadDocumentRequest(
                        new org.springframework.mock.web.MockMultipartFile(
                                "file",
                                "document.pdf",
                                "application/pdf",
                                new byte[] {1})));

        assertTrue(violations.isEmpty());
    }
}
