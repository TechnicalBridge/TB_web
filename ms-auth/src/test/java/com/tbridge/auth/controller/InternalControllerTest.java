package com.tbridge.auth.controller;

import com.tbridge.auth.config.SecurityConfig;
import com.tbridge.auth.dto.response.CodigoEmitidoResponse;
import com.tbridge.auth.service.AuthService;
import com.tbridge.auth.service.MailService;
import com.tbridge.common.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class InternalControllerTest {

    private static final String CUERPO = """
            {"rut":"16482337-7","canales":["correo"],"correo":"felipe.rojas@correo.cl","acreedor":"Patrimonio Inmuebles"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private MailService correo;

    @Test
    void sin_la_clave_interna_nadie_emite_codigos() throws Exception {
        mvc.perform(post("/internal/codigos").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/codigos").header("X-Internal-Key", "otra")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        verify(auth, never()).emitirCodigo(any(), any(), any());
    }

    @Test
    void con_la_clave_emite_y_manda_el_correo() throws Exception {
        when(auth.emitirCodigo("16482337-7", List.of("correo"), null)).thenReturn(new CodigoEmitidoResponse(
                "K7M2QX", "16482337-7", List.of("correo"), Instant.parse("2026-09-25T12:00:00Z"), "Entra a ..."));

        mvc.perform(post("/internal/codigos").header("X-Internal-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("K7M2QX"))
                .andExpect(jsonPath("$.expiraEn").value("2026-09-25T12:00:00Z"));

        verify(correo).enviarCodigo("felipe.rojas@correo.cl", "K7M2QX", "Patrimonio Inmuebles");
    }

    @Test
    void con_fecha_de_vencimiento_el_correo_es_el_recordatorio() throws Exception {
        when(auth.emitirCodigo("16482337-7", List.of("correo"), null)).thenReturn(new CodigoEmitidoResponse(
                "P4R8TW", "16482337-7", List.of("correo"), Instant.parse("2026-10-18T12:00:00Z"), "Entra a ..."));

        mvc.perform(post("/internal/codigos").header("X-Internal-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.replace("}", ",\"vence\":\"2026-10-20\"}")))
                .andExpect(status().isOk());

        verify(correo).enviarRecordatorio("felipe.rojas@correo.cl", "P4R8TW", "Patrimonio Inmuebles",
                LocalDate.of(2026, 10, 20));
        verify(correo, never()).enviarCodigo(any(), any(), any());
    }

    @Test
    void sin_rut_es_400() throws Exception {
        mvc.perform(post("/internal/codigos").header("X-Internal-Key", "clave-interna-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"canales\":[\"correo\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el RUT"));
    }
}
