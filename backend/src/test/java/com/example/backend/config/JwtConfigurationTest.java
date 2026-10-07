package com.example.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class JwtConfigurationTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void acceptsAValidBase64EncodedHmacKeyOfAtLeast256Bits() {
        byte[] keyBytes = new byte[32];

        var key = configuration.jwtSigningKey(Base64.getEncoder().encodeToString(keyBytes));

        assertEquals("HmacSHA256", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    void failsClearlyForMissingMalformedOrWeakSigningKeys() {
        assertThrows(IllegalStateException.class, () -> configuration.jwtSigningKey(""));
        assertThrows(IllegalStateException.class, () -> configuration.jwtSigningKey("not-base64"));
        assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
