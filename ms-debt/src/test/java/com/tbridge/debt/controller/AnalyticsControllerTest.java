package com.tbridge.debt.controller;

import com.tbridge.common.exception.ApiException;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.debt.config.SecurityConfig;
import com.tbridge.debt.dto.response.ResumenResponse;
import com.tbridge.debt.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, JwtService.class})
@ActiveProfiles("test")
class AnalyticsControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private AnalyticsService analytics;

    @Test
    void la_empresa_ve_el_resumen_de_su_cartera() throws Exception {
        when(analytics.resumenPara(any())).thenReturn(new ResumenResponse("APOFYX", "77305118-6", 3, 2, 1, 1, 0,
                List.of(new ResumenResponse.PorMoneda("CLP", new BigDecimal("1040000"), new BigDecimal("410000"),
                        new BigDecimal("28.3"))),
                List.of(), Map.of()));

        mvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + jwt.issue("1", "c@apofyx.cl", "CREDITOR", "Camila", "77305118-6")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizacion").value("APOFYX"))
                .andExpect(jsonPath("$.porMoneda[0].tasaRecuperacion").value(28.3))
                .andExpect(jsonPath("$._links.deudas.href").value("http://localhost/api/debts"));
    }

    @Test
    void un_deudor_no_ve_el_resumen_de_nadie() throws Exception {
        when(analytics.resumenPara(any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Solo el acreedor ve el resumen"));

        mvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + jwt.issue("16482337-7", null, "DEBTOR", null, "16482337-7")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Solo el acreedor ve el resumen"));
    }
}
