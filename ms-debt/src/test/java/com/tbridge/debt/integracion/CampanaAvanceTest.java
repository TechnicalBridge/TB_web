package com.tbridge.debt.integracion;

import com.tbridge.debt.domain.Campaign;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtEvent;
import com.tbridge.debt.repo.CampaignRepository;
import com.tbridge.debt.repo.DebtEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampanaAvanceTest {

    private final CampaignRepository campanas = mock(CampaignRepository.class);
    private final DebtRepository debts = mock(DebtRepository.class);
    private final DebtEventRepository eventos = mock(DebtEventRepository.class);
    private final EventosService avisos = mock(EventosService.class);
    private final CampanaAvanceService servicio = new CampanaAvanceService(campanas, debts, eventos, avisos);

    private static Debt deuda(Debt.Currency moneda) {
        Debt d = new Debt();
        d.setCurrency(moneda);
        return d;
    }

    private Map<String, Object> avanceCon(List<Debt> cartera, List<DebtEvent> historia) {
        Campaign campana = new Campaign();
        campana.setExternalId("APX-CMP-8");
        when(debts.findByCampaign(campana)).thenReturn(cartera);
        when(eventos.findByDebtIn(anyList())).thenReturn(historia);
        return servicio.avance(campana);
    }

    @Test
    void cuentaElEmbudoDeLaCampana() {
        Debt una = deuda(Debt.Currency.CLP);
        Map<String, Object> datos = avanceCon(List.of(una), List.of(
                DebtEvent.de(una, DebtEvent.Type.code_sent, DebtEvent.Actor.agency),
                DebtEvent.de(una, DebtEvent.Type.portal_entered, DebtEvent.Actor.debtor),
                DebtEvent.de(una, DebtEvent.Type.repacted, DebtEvent.Actor.debtor),
                DebtEvent.de(una, DebtEvent.Type.payment_applied, DebtEvent.Actor.system)
                        .conMonto(new BigDecimal("410000"), Debt.Currency.CLP)));

        assertEquals("APX-CMP-8", datos.get("campana_id_externo"));
        assertEquals(1, datos.get("deudas"));
        assertEquals(1, datos.get("enviados"));
        assertEquals(1, datos.get("ingresos_portal"));
        assertEquals(1, datos.get("repactaciones"));
        assertEquals(1, datos.get("pagos"));
        assertEquals(410000L, datos.get("recuperado_clp"));
    }

    @Test
    void losPesosYLasUfNoSeSuman() {
        Debt enPesos = deuda(Debt.Currency.CLP);
        Debt enUf = deuda(Debt.Currency.UF);
        Map<String, Object> datos = avanceCon(List.of(enPesos, enUf), List.of(
                DebtEvent.de(enPesos, DebtEvent.Type.payment_applied, DebtEvent.Actor.system)
                        .conMonto(new BigDecimal("410000"), Debt.Currency.CLP),
                DebtEvent.de(enUf, DebtEvent.Type.payment_applied, DebtEvent.Actor.system)
                        .conMonto(new BigDecimal("19.25"), Debt.Currency.UF)));

        assertEquals(2, datos.get("pagos"));
        assertEquals(410000L, datos.get("recuperado_clp"));
        assertEquals(new BigDecimal("19.25"), datos.get("recuperado_uf"));
    }

    /**
     * Lo que DataBridge no mide no viaja en cero: un cero diria "ninguno" y lo
     * que corresponde decir es "no lo se".
     */
    @Test
    void loQueNoSeMideNoViaja() {
        Map<String, Object> datos = avanceCon(List.of(), List.of());
        for (String noMedido : List.of("entregados", "abiertos", "respuestas", "bajas", "reportes_fraude")) {
            assertFalse(datos.containsKey(noMedido), noMedido);
        }
        assertEquals(0, datos.get("deudas"));
    }
}
