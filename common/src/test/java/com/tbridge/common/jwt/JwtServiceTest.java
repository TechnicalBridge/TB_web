package com.tbridge.common.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    /** 32 bytes justos: el minimo que acepta HMAC-SHA256. */
    private static final String SECRETO = "unit-test-secret-key-32-chars!!!";

    private final JwtService jwt = new JwtService(SECRETO, 15);

    @Test
    void roundTripClaims() {
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
        String token = jwt.issue("u-1", "ana@correo.com", "DEBTOR", "Ana");
        assertThrows(Exception.class, () -> jwt.parse(token + "x"));
    }

    @Test
    void el_token_vive_lo_que_dice_la_configuracion_y_no_una_semana() {
        Claims claims = jwt.parse(jwt.issue("u-1", null, "DEBTOR", null, "16482337-7"));
        long segundos = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertEquals(Duration.ofMinutes(15).toSeconds(), segundos);
    }

    @Test
    void un_secreto_corto_no_arranca_en_vez_de_rellenarse_con_ceros() {
        //  Antes se completaba con ceros hasta 32 bytes y el servicio arrancaba
        //  igual, con una llave de casi nada de entropia.
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new JwtService("corto", 15));
        assertTrue(error.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void un_secreto_de_31_bytes_tampoco() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("unit-test-secret-key-32-chars!!", 15));
    }
}
