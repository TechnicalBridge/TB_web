package com.tbridge.ai.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Como lee el asistente la respuesta de ms-debt, y que pasa cuando ms-debt no responde. */
class DeudasClientTest {

    private static final MediaType HAL = MediaType.parseMediaType("application/hal+json");

    private MockRestServiceServer servidor;
    private DeudasClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new DeudasClient(builder, "http://ms-debt:8083/");
    }

    @Test
    void las_deudas_vienen_en_embedded() {
        Map<String, Object> respuesta = Map.of("_embedded", Map.of("debts", List.of(Map.of("id", 3, "saldo", 96.25))),
                "_links", Map.of());
        assertEquals(List.of(Map.of("id", 3, "saldo", 96.25)), DeudasClient.deudasDe(respuesta));
    }

    @Test
    void sin_deudas_no_viene_embedded_y_es_una_lista_vacia() {
        assertEquals(List.of(), DeudasClient.deudasDe(
                Map.of("_links", Map.of("self", Map.of("href", "http://localhost:8080/api/debts")))));
        assertEquals(List.of(), DeudasClient.deudasDe(null));
    }

    @Test
    void las_pide_con_la_sesion_del_deudor() {
        servidor.expect(requestTo("http://ms-debt:8083/api/debts"))
                .andExpect(header("Authorization", "Bearer jwt-del-deudor"))
                .andRespond(withSuccess("""
                        {"_embedded":{"debts":[{"id":3,"acreedor":"Patrimonio Inmuebles","saldo":96.25,"moneda":"UF"}]}}""",
                        HAL));

        List<Map<String, Object>> deudas = cliente.deudasDelDeudor("Bearer jwt-del-deudor");

        assertEquals(1, deudas.size());
        assertEquals("Patrimonio Inmuebles", deudas.getFirst().get("acreedor"));
        assertEquals(96.25, deudas.getFirst().get("saldo"));
        servidor.verify();
    }

    @Test
    void si_ms_debt_dice_que_no_el_asistente_sigue_sin_deudas() {
        servidor.expect(requestTo("http://ms-debt:8083/api/debts")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertEquals(List.of(), cliente.deudasDelDeudor("Bearer vencido"));
    }

    @Test
    void si_ms_debt_falla_el_asistente_sigue_sin_deudas() {
        servidor.expect(requestTo("http://ms-debt:8083/api/debts")).andRespond(withServerError());

        assertEquals(List.of(), cliente.deudasDelDeudor("Bearer jwt-del-deudor"));
    }

    @Test
    void sin_sesion_no_pregunta() {
        assertEquals(List.of(), cliente.deudasDelDeudor(null));
        servidor.verify();
    }
}
