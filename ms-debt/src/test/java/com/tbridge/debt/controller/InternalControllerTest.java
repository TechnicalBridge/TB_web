package com.tbridge.debt.controller;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.debt.config.SecurityConfig;
import com.tbridge.debt.dto.response.DebtSnapshotResponse;
import com.tbridge.debt.service.ApiKeyService;
import com.tbridge.debt.service.CampanaAvanceService;
import com.tbridge.debt.service.DebtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lo interno: sin la clave, nada; con ella, lo que ms-payments necesita. */
@WebMvcTest(InternalController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class InternalControllerTest {

    private static final String CLAVE = "clave-interna-de-prueba";

    @Autowired private MockMvc mvc;
    @MockitoBean private DebtService debts;
    @MockitoBean private CampanaAvanceService avances;
    @MockitoBean private ApiKeyService claves;

    @Test
    void sin_la_clave_interna_no_se_revela_ninguna_deuda() throws Exception {
        mvc.perform(get("/internal/debts/3")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/debts/3").header("X-Internal-Key", "otra")).andExpect(status().isUnauthorized());
        verify(debts, never()).snapshotInterno(any(), any());
    }

    @Test
    void con_la_clave_ms_payments_sabe_cuanto_cobrar() throws Exception {
        when(debts.snapshotInterno(3L, null)).thenReturn(new DebtSnapshotResponse(
                3L, "76418902-7", "18905214-6", "CLP", new BigDecimal("410000"), 12L));

        mvc.perform(get("/internal/debts/3").header("X-Internal-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(410000))
                .andExpect(jsonPath("$.debtorRut").value("18905214-6"))
                .andExpect(jsonPath("$.installmentId").value(12));
    }

    @Test
    void el_aviso_de_pago_por_http_llega_entero() throws Exception {
        mvc.perform(post("/internal/events/pago-confirmado").header("X-Internal-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"pago.confirmado","paymentId":41,"debtId":3,"debtorRut":"18905214-6",
                                 "amount":410000,"currency":"CLP","amountClp":410000,"gateway":"webpay",
                                 "gatewayTxnId":"wp-1","paidAt":"2026-09-24T12:00:00Z"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        ArgumentCaptor<PagoConfirmado> aviso = ArgumentCaptor.forClass(PagoConfirmado.class);
        verify(debts).onPagoConfirmado(aviso.capture());
        assertEquals(3L, aviso.getValue().debtId());
        assertEquals("wp-1", aviso.getValue().gatewayTxnId());
    }

    @Test
    void emitir_una_clave_pide_el_rut() throws Exception {
        mvc.perform(post("/internal/claves").header("X-Internal-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Demo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el RUT de la organizacion"));
    }
}
