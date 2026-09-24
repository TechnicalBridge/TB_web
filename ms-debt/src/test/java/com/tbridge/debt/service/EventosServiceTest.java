package com.tbridge.debt.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.dto.evento.PagoConfirmadoDatos;
import com.tbridge.debt.dto.request.SuscripcionRequest;
import com.tbridge.debt.exception.CarteraInvalida;
import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.model.OutboxEvent;
import com.tbridge.debt.model.Subscription;
import com.tbridge.debt.repository.OutboxEventRepository;
import com.tbridge.debt.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los eventos del contrato 3 salen con la forma exacta que esperan APOFYX y
 * Patrimonio. El cuerpo va firmado: un campo de mas, de menos o renombrado es
 * un evento que el receptor no sabe leer.
 */
@ExtendWith(MockitoExtension.class)
class EventosServiceTest {

    /** El mismo ObjectMapper que arma Spring Boot con application.properties. */
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    @Mock private SubscriptionRepository suscripciones;
    @Mock private OutboxEventRepository bandeja;

    private EventosService eventos;
    private Organization apofyx;
    private Debt deuda;

    @BeforeEach
    void preparar() {
        eventos = new EventosService(suscripciones, bandeja, json);
        apofyx = new Organization();
        apofyx.setRut("77305118-6");
        Organization patrimonio = new Organization();
        patrimonio.setRut("76418902-7");
        Batch lote = new Batch();
        lote.setSender(apofyx);
        lote.setExternalId("APX-2026-09-19-004");
        deuda = new Debt();
        deuda.setCreditor(patrimonio);
        deuda.setLastBatch(lote);
        deuda.setExternalId("CTR-2026-031");
    }

    private Subscription suscripcion(String eventos) {
        Subscription s = new Subscription();
        s.setOrganization(apofyx);
        s.setUrl("http://apofyx/api/v1/eventos");
        s.setSecret("whsec_prueba");
        s.setEvents(eventos);
        return s;
    }

    @Test
    void pago_confirmado_en_pesos_sale_con_la_forma_del_contrato() throws Exception {
        when(suscripciones.findByOrganizationAndActiveTrue(apofyx)).thenReturn(List.of(suscripcion(null)));
        Instant pagado = Instant.parse("2026-09-20T17:03:11Z");

        eventos.publicar(deuda, EventosService.PAGO_CONFIRMADO, new PagoConfirmadoDatos("CTR-2026-031", "41",
                EventosService.monto(new BigDecimal("410000.00"), Debt.Currency.CLP), "CLP", 410000L, null,
                "webpay", EventosService.enChile(pagado)), pagado);

        ArgumentCaptor<OutboxEvent> anotado = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(bandeja).save(anotado.capture());
        String cuerpo = anotado.getValue().getPayload();
        String sinId = cuerpo.replaceFirst("\"id\":\"evt_[0-9a-f-]{36}\",", "");
        assertEquals("""
                {"tipo":"pago.confirmado","version":"1","ocurrido_en":"2026-09-20T14:03:11-03:00",\
                "acreedor_rut":"76418902-7","lote_id_externo":"APX-2026-09-19-004",\
                "datos":{"deuda_id_externo":"CTR-2026-031","pago_id":"41","monto":410000,"moneda":"CLP",\
                "monto_clp":410000,"medio":"webpay","pagado_en":"2026-09-20T14:03:11-03:00"}}""", sinId);
        assertTrue(cuerpo.startsWith("{\"id\":\"evt_"), cuerpo);
    }

    @Test
    void en_uf_el_monto_lleva_decimales_y_el_valor_de_la_uf() throws Exception {
        when(suscripciones.findByOrganizationAndActiveTrue(apofyx)).thenReturn(List.of(suscripcion(null)));

        eventos.publicar(deuda, EventosService.PAGO_CONFIRMADO, new PagoConfirmadoDatos("CTR-2024-007", "42",
                EventosService.monto(new BigDecimal("38.50"), Debt.Currency.UF), "UF", 1535247L,
                new BigDecimal("39876.54"), "khipu", "2026-09-20T14:03:11-03:00"), Instant.now());

        ArgumentCaptor<OutboxEvent> anotado = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(bandeja).save(anotado.capture());
        var datos = json.readTree(anotado.getValue().getPayload()).get("datos");
        assertEquals("38.5", datos.get("monto").decimalValue().toPlainString());
        assertEquals("39876.54", datos.get("valor_uf").decimalValue().toPlainString());
    }

    @Test
    void sin_suscriptores_no_se_anota_nada_y_el_hecho_ocurre_igual() {
        when(suscripciones.findByOrganizationAndActiveTrue(apofyx)).thenReturn(List.of());

        assertEquals(0, eventos.publicar(deuda, EventosService.DEUDA_SALDADA, "x", Instant.now()));
        verify(bandeja, never()).save(any());
    }

    @Test
    void quien_pidio_solo_algunos_eventos_recibe_solo_esos() {
        when(suscripciones.findByOrganizationAndActiveTrue(apofyx))
                .thenReturn(List.of(suscripcion("[\"deuda.saldada\"]")));

        assertEquals(0, eventos.publicar(deuda, EventosService.PAGO_CONFIRMADO, "x", Instant.now()));
        assertEquals(1, eventos.publicar(deuda, EventosService.DEUDA_SALDADA, "x", Instant.now()));
    }

    @Test
    void suscribirse_dos_veces_devuelve_el_mismo_secreto() {
        Subscription existente = suscripcion(null);
        when(suscripciones.findByOrganizationAndUrl(apofyx, "http://apofyx/api/v1/eventos"))
                .thenReturn(Optional.of(existente));

        var respuesta = eventos.suscribir(apofyx, new SuscripcionRequest("http://apofyx/api/v1/eventos", null));

        assertEquals("whsec_prueba", respuesta.secreto());
        assertEquals("todos", respuesta.eventos());
    }

    @Test
    void un_evento_que_no_esta_en_el_contrato_se_rechaza() {
        CarteraInvalida error = assertThrows(CarteraInvalida.class, () -> eventos.suscribir(apofyx,
                new SuscripcionRequest("http://apofyx/api/v1/eventos", List.of("deuda.inventada"))));
        assertEquals("evento_desconocido", error.getCodigo());
    }
}
