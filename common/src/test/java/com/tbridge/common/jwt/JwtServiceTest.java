package com.tbridge.common.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void roundTripClaims() {
        JwtService jwt = new JwtService("unit-test-secret-key-32-chars!!");
        String token = jwt.issue("u-1", "ana@correo.com", "DEBTOR", "Ana Pérez");
        JwtPrincipal principal = jwt.toPrincipal(jwt.parse(token));
        assertEquals("u-1", principal.id());
        assertEquals("ana@correo.com", principal.email());
        assertEquals("DEBTOR", principal.role());
        assertEquals("Ana Pérez", principal.name());
        assertTrue(principal.isDebtor());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = new JwtService("unit-test-secret-key-32-chars!!");
        String token = jwt.issue("u-1", "ana@correo.com", "DEBTOR", "Ana");
        assertThrows(Exception.class, () -> jwt.parse(token + "x"));
    }
}
