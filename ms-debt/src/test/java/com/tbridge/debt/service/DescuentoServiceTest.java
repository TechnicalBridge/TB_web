package com.tbridge.debt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Mandate;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.MandateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** Cuanto de la mora se condona: el tramo de la deuda, recortado al maximo que autoriza el acreedor. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DescuentoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 8);

    @Mock private MandateRepository mandates;

    private DescuentoService servicio;
    private Organization apofyx;
    private Organization patrimonio;
    private Mandate mandato;
    private Campaign campana;
    private Debt deuda;

    @BeforeEach
    void preparar() {
        servicio = new DescuentoService(mandates, new ObjectMapper());
        apofyx = organizacion(1L);
        patrimonio = organizacion(2L);
        mandato = new Mandate();
        mandato.setValidFrom(LocalDate.of(2026, 9, 1));
        mandato.setMaxMoraDiscount(new BigDecimal("100"));
        when(mandates.findByAgencyAndCreditorAndStatus(apofyx, patrimonio, Mandate.Status.active))
                .thenReturn(List.of(mandato));

        campana = new Campaign();
        campana.setAgency(apofyx);
        campana.setCreditor(patrimonio);
        campana.setStartsOn(LocalDate.of(2026, 9, 19));
        campana.setStatus(Campaign.Status.running);
        campana.setMoraDiscount("{\"1-30\":0,\"31-90\":50,\"91-120\":100}");
        deuda = new Debt();
        deuda.setCampaign(campana);
    }

    private static Organization organizacion(long id) {
        Organization o = new Organization();
        o.setId(id);
        return o;
    }

    @Test
    void el_tramo_sale_de_los_dias_del_cargo_mas_antiguo() {
        assertEquals("1-30", DescuentoService.tramoDe(30));
        assertEquals("31-90", DescuentoService.tramoDe(31));
        assertEquals("31-90", DescuentoService.tramoDe(62));
        assertEquals("91-120", DescuentoService.tramoDe(100));
        //  Una deuda de un acreedor directo puede pasar los 120: no recibe menos que una de 120.
        assertEquals("91-120", DescuentoService.tramoDe(150));
    }

    @Test
    void con_62_dias_se_condona_la_mitad_y_con_mas_de_90_toda() {
        assertEquals(new BigDecimal("50"), servicio.porcentaje(deuda, 62, HOY));
        assertEquals(new BigDecimal("100"), servicio.porcentaje(deuda, 100, HOY));
        assertEquals(new BigDecimal("0"), servicio.porcentaje(deuda, 30, HOY));
    }

    @Test
    void si_el_acreedor_baja_su_maximo_el_descuento_baja_con_el() {
        mandato.setMaxMoraDiscount(new BigDecimal("30"));

        assertEquals(new BigDecimal("30"), servicio.porcentaje(deuda, 100, HOY));
        assertEquals(new BigDecimal("30"), servicio.porcentaje(deuda, 62, HOY));
    }

    @Test
    void sin_maximo_autorizado_no_hay_descuento() {
        mandato.setMaxMoraDiscount(null);

        assertEquals(0, servicio.porcentaje(deuda, 100, HOY).signum());
    }

    @Test
    void con_el_mandato_vencido_no_hay_descuento() {
        mandato.setValidTo(LocalDate.of(2026, 10, 1));

        assertEquals(0, servicio.porcentaje(deuda, 100, HOY).signum());
    }

    @Test
    void solo_con_la_campana_en_curso_y_dentro_de_sus_fechas() {
        campana.setStatus(Campaign.Status.paused);
        assertEquals(0, servicio.porcentaje(deuda, 62, HOY).signum());

        campana.setStatus(Campaign.Status.running);
        campana.setEndsOn(LocalDate.of(2026, 10, 7));
        assertEquals(0, servicio.porcentaje(deuda, 62, HOY).signum());

        campana.setEndsOn(null);
        campana.setStartsOn(LocalDate.of(2026, 10, 9));
        assertEquals(0, servicio.porcentaje(deuda, 62, HOY).signum());
    }

    @Test
    void sin_campana_o_sin_descuento_no_hay_nada() {
        campana.setMoraDiscount(null);
        assertEquals(0, servicio.porcentaje(deuda, 62, HOY).signum());

        deuda.setCampaign(null);
        assertEquals(0, servicio.porcentaje(deuda, 62, HOY).signum());
    }

    @Test
    void el_acreedor_que_cobra_sin_agencia_se_autoriza_a_si_mismo() {
        campana.setAgency(patrimonio);

        assertEquals(new BigDecimal("100"), servicio.porcentaje(deuda, 100, HOY));
    }
}
