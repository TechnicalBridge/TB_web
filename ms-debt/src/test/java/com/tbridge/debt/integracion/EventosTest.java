package com.tbridge.debt.integracion;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventosTest {

    /**
     * La firma de referencia se calculo con el crypto de Node, que es con lo
     * que Patrimonio verifica. Si Java firmara distinto —otro orden, otra
     * codificacion—, todo evento llegaria rechazado y nadie sabria por que.
     */
    @Test
    void firmaIgualQueElReceptor() {
        String cuerpo = "{\"id\":\"evt_1\",\"tipo\":\"pago.confirmado\"}";
        assertEquals("v1=344ce2850a1c9cb0b60434906199eb2d716de503c6191265746204fcaa31d3f6",
                EventDispatcher.firma("whsec_prueba", 1789923791L, cuerpo));
    }

    @Test
    void otroTimestampEsOtraFirma() {
        String cuerpo = "{\"id\":\"evt_1\"}";
        assertNotEquals(EventDispatcher.firma("s", 1L, cuerpo), EventDispatcher.firma("s", 2L, cuerpo));
    }

    @Test
    void lasFechasVanConElDesfaseDeChile() {
        // El 20 de septiembre Chile ya esta en horario de verano (UTC-3): 17:03 UTC son las 14:03.
        String enChile = EventosService.enChile(Instant.parse("2026-09-20T17:03:11.987Z"));
        assertTrue(enChile.startsWith("2026-09-20T14:03:11"), enChile);
        assertTrue(enChile.endsWith("-03:00"), enChile);
    }

    @Test
    void losPesosViajanEnterosYLaUfConDecimales() {
        assertEquals(520000L, EventosService.monto(new BigDecimal("520000.00"), Debt.Currency.CLP));
        assertEquals(new BigDecimal("38.5"), EventosService.monto(new BigDecimal("38.50"), Debt.Currency.UF));
    }

    @Test
    void despuesDelSextoIntentoQuedaFallido() {
        OutboxEvent evento = OutboxEvent.para(null, "x", "pago.confirmado", "{}", Instant.now());
        for (int i = 0; i < 5; i++) {
            evento.fallo("caido");
            assertEquals(OutboxEvent.Status.pending, evento.getStatus());
        }
        evento.fallo("caido");
        assertEquals(OutboxEvent.Status.failed, evento.getStatus());
        assertEquals((short) 6, evento.getAttempts());
    }
}
