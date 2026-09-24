package com.tbridge.auth.service;

import com.tbridge.auth.domain.AccessCode;
import com.tbridge.auth.domain.AccessLog;
import com.tbridge.auth.repo.AccessCodeRepository;
import com.tbridge.auth.repo.AccessLogRepository;
import com.tbridge.auth.repo.MagicLinkRepository;
import com.tbridge.auth.repo.StaffUserRepository;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El acceso del deudor.
 *
 * Lo que se prueba aca no es que "funcione el login": es que las reglas que
 * sostienen la tesis del caso se cumplan. Un codigo de un solo uso, que expira,
 * que se puede agotar a intentos, y que en la base no queda escrito.
 */
class AuthServiceTest {

    private final List<AccessCode> guardados = new ArrayList<>();
    private final List<AccessLog> bitacora = new ArrayList<>();
    private final SesionesEnMemoria sesiones = new SesionesEnMemoria();
    private AuthService auth;

    @BeforeEach
    void preparar() {
        AccessCodeRepository codigos = mock(AccessCodeRepository.class);
        MagicLinkRepository enlaces = mock(MagicLinkRepository.class);
        StaffUserRepository personal = mock(StaffUserRepository.class);
        AccessLogRepository registro = mock(AccessLogRepository.class);
        MailService correo = mock(MailService.class);
        JwtService jwt = new JwtService("unit-test-secret-key-32-chars!!!", 15);
        SessionService sesiones = new SessionService(this.sesiones.repositorio,
                java.time.Duration.ofDays(7), java.time.Clock.systemUTC());

        when(codigos.save(any())).thenAnswer(llamada -> {
            AccessCode codigo = llamada.getArgument(0);
            guardados.remove(codigo);
            guardados.add(codigo);
            return codigo;
        });
        when(codigos.findFirstByDebtorRutAndConsumedAtIsNullOrderByIssuedAtDesc(any()))
                .thenAnswer(llamada -> guardados.stream()
                        .filter(c -> c.getDebtorRut().equals(llamada.getArgument(0)))
                        .filter(c -> c.getConsumedAt() == null)
                        .reduce((primero, ultimo) -> ultimo));
        when(registro.save(any())).thenAnswer(llamada -> {
            bitacora.add(llamada.getArgument(0));
            return llamada.getArgument(0);
        });
        when(registro.findByIpHashAndOccurredAtAfter(any(), any()))
                .thenAnswer(llamada -> bitacora.stream()
                        .filter(r -> llamada.getArgument(0).equals(r.getIpHash()))
                        .toList());
        when(personal.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(enlaces.findByTokenHash(any())).thenReturn(Optional.empty());

        auth = new AuthService(codigos, enlaces, personal, registro, jwt, sesiones, correo,
                "http://localhost:5173", 24, 15);
    }

    private String emitir(String rut) {
        return String.valueOf(auth.emitirCodigo(rut, List.of("whatsapp", "correo"), "prueba")
                .get("codigo"));
    }

    @Test
    void el_codigo_abre_la_sesion_y_lleva_el_rut() {
        String codigo = emitir("16482337-7");
        AuthService.Sesion sesion = auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4");

        assertNotNull(sesion.token());
        assertEquals("16482337-7", sesion.user().get("rut"));
    }

    @Test
    void entrar_abre_una_sesion_revocable_y_la_llave_no_queda_escrita() {
        String codigo = emitir("16482337-7");
        AuthService.Sesion sesion = auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4");

        assertEquals(1, sesiones.filas.size());
        String guardada = sesiones.filas.get(0).getRefreshHash();
        assertNotEquals(sesion.llave().valor(), guardada);
        assertEquals(SessionService.huella(sesion.llave().valor()), guardada);
    }

    @Test
    void el_codigo_no_queda_escrito_en_la_base() {
        String codigo = emitir("16482337-7");
        AccessCode guardado = guardados.getFirst();

        assertNotEquals(codigo, guardado.getCodeHash());
        assertFalse(guardado.getCodeHash().contains(codigo));
        assertEquals(64, guardado.getCodeHash().length(), "es un SHA-256 en hexadecimal");
    }

    @Test
    void el_codigo_sirve_una_sola_vez() {
        String codigo = emitir("16482337-7");
        auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4");

        ApiException segunda = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4"));
        assertEquals(HttpStatus.UNAUTHORIZED, segunda.getStatus());
    }

    @Test
    void el_codigo_de_otro_rut_no_sirve() {
        emitir("16482337-7");
        String deValentina = emitir("18905214-6");

        assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", deValentina, "1.2.3.4"));
    }

    @Test
    void el_codigo_se_agota_a_intentos() {
        String codigo = emitir("16482337-7");

        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class,
                    () -> auth.entrarConCodigo("16482337-7", "ZZZZZZ", "9.9.9." + System.nanoTime()));
        }
        //  Agotado: ni siquiera el codigo correcto entra.
        ApiException agotado = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, agotado.getStatus());
    }

    @Test
    void el_codigo_expira() {
        String codigo = emitir("16482337-7");
        guardados.getFirst().setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        ApiException vencido = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", codigo, "1.2.3.4"));
        assertTrue(vencido.getMessage().contains("expiro"));
    }

    @Test
    void demasiados_intentos_desde_el_mismo_origen_se_frenan() {
        emitir("16482337-7");
        for (int i = 0; i < 10; i++) {
            try {
                auth.entrarConCodigo("16482337-7", "ZZZZZZ", "5.5.5.5");
            } catch (ApiException ignorada) {
                // cada intento fallido queda anotado
            }
        }
        ApiException frenado = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", "ZZZZZZ", "5.5.5.5"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, frenado.getStatus());
    }

    @Test
    void un_rut_con_digito_verificador_falso_no_entra() {
        ApiException malo = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-8", "ABC123", "1.2.3.4"));
        assertEquals(HttpStatus.BAD_REQUEST, malo.getStatus());
    }

    @Test
    void el_codigo_no_trae_caracteres_ambiguos() {
        for (int i = 0; i < 200; i++) {
            String codigo = emitir("16482337-7");
            assertEquals(6, codigo.length());
            for (char caracter : "01OIL".toCharArray()) {
                assertFalse(codigo.indexOf(caracter) >= 0,
                        "el codigo se dicta por telefono: " + codigo + " trae " + caracter);
            }
        }
    }

    @Test
    void el_mensaje_no_distingue_entre_rut_sin_deuda_y_codigo_equivocado() {
        String codigo = emitir("16482337-7");
        String sinCodigo = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("18905214-6", codigo, "1.2.3.4")).getMessage();
        String equivocado = assertThrows(ApiException.class,
                () -> auth.entrarConCodigo("16482337-7", "ZZZZZZ", "1.2.3.4")).getMessage();

        //  Si fueran distintos, se podria averiguar que RUT tienen deuda.
        assertEquals(sinCodigo, equivocado);
    }
}
