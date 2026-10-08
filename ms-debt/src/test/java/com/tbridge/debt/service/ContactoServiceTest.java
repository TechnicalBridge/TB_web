package com.tbridge.debt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.client.AuthClient;
import com.tbridge.debt.dto.response.ContactosResponse;
import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtEvent;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.CampaignRepository;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.DebtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La campana la ejecuta DataBridge: los toques segun la cadencia, dentro de
 * las fechas y los intentos, y solo cuando la ley lo permite.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactoServiceTest {

    private static final ZoneId CHILE = ZoneId.of("America/Santiago");
    //  La deuda llega el lunes 5 de octubre de 2026; el 12 es feriado.
    private static final LocalDate ENTRADA = LocalDate.of(2026, 10, 5);

    @Mock private CampaignRepository campaigns;
    @Mock private DebtRepository debts;
    @Mock private DebtEventRepository events;
    @Mock private AuthClient auth;
    @Mock private LimiteDeContacto limite;

    private ContactoService servicio;
    private Campaign campana;
    private Debt deuda;
    private final List<DebtEvent> historia = new ArrayList<>();

    @BeforeEach
    void preparar() {
        servicio = new ContactoService(campaigns, debts, events, auth, limite, new ObjectMapper(), "2026-10-12");

        Organization patrimonio = new Organization();
        patrimonio.setTradeName("Patrimonio Inmuebles");
        campana = new Campaign();
        campana.setId(8L);
        campana.setName("Arriendos octubre");
        campana.setStartsOn(LocalDate.of(2026, 10, 1));
        campana.setEndsOn(LocalDate.of(2026, 11, 30));
        campana.setChannels("[\"correo\"]");
        campana.setAttempts((short) 2);
        campana.setCadenceDays("[1, 4, 11]");
        campana.setStatus(Campaign.Status.running);

        Debtor deudor = new Debtor();
        deudor.setRut("16482337-7");
        deudor.setEmail("felipe.rojas@correo.cl");
        deuda = new Debt();
        deuda.setId(3L);
        deuda.setCreditor(patrimonio);
        deuda.setDebtor(deudor);
        deuda.setExternalId("CTR-2025-014");
        deuda.setStatus(Debt.Status.open);
        deuda.setCreatedAt(ENTRADA.atTime(9, 0).atZone(CHILE).toInstant());

        when(campaigns.findByStatus(Campaign.Status.running)).thenReturn(List.of(campana));
        when(debts.findByCampaign(campana)).thenReturn(List.of(deuda));
        when(events.findByDebtInAndType(anyList(), any())).thenReturn(historia);
        when(events.save(any())).thenAnswer(llamada -> {
            historia.add(llamada.getArgument(0));
            return llamada.getArgument(0);
        });
        when(limite.permite(any(), any())).thenReturn(true);
    }

    private ContactosResponse el(int dia, int hora) {
        return servicio.contactar(LocalDateTime.of(2026, 10, dia, hora, 0), null);
    }

    @Test
    void cada_toque_sale_el_dia_que_dice_la_cadencia() {
        assertEquals(0, el(5, 10).enviados(), "el dia que entra todavia no");
        assertEquals(1, el(6, 10).enviados(), "dia 1");
        assertEquals(0, el(7, 10).enviados(), "el segundo es el dia 4");
        assertEquals(1, el(9, 10).enviados(), "dia 4");

        DebtEvent primero = historia.getFirst();
        assertEquals(DebtEvent.Type.code_sent, primero.getType());
        assertEquals("{\"campana\":8,\"toque\":1}", primero.getDetail());
        assertEquals("campana Arriendos octubre, toque 1 de 2", primero.getReference());
        verify(auth, org.mockito.Mockito.times(2)).emitirCodigo(any());
    }

    @Test
    void no_pasa_de_los_intentos() {
        el(6, 10);
        el(9, 10);

        assertEquals(0, el(16, 10).enviados(), "dos intentos, aunque la cadencia tenga un dia 11");
    }

    @Test
    void ni_de_noche_ni_en_domingo_ni_un_feriado() {
        assertFalse(el(6, 7).enHorario(), "antes de las 8");
        assertFalse(el(6, 20).enHorario(), "desde las 20");
        assertFalse(el(11, 10).enHorario(), "domingo");
        assertFalse(el(12, 10).enHorario(), "feriado");
        verify(auth, never()).emitirCodigo(any());

        assertTrue(el(10, 10).enHorario(), "el sabado si");
        assertEquals(1, historia.size(), "y sale el toque que estaba pendiente");
    }

    @Test
    void si_la_ley_no_deja_escribirle_hoy_sale_despues() {
        when(limite.permite(any(), any())).thenReturn(false);
        ContactosResponse hoy = el(6, 10);
        assertEquals(0, hoy.enviados());
        assertEquals(1, hoy.omitidos());

        when(limite.permite(any(), any())).thenReturn(true);
        assertEquals(1, el(8, 10).enviados(), "el toque pendiente sale en cuanto se puede");
    }

    @Test
    void una_deuda_pagada_repactada_o_en_reclamo_ya_no_se_contacta() {
        for (Debt.Status estado : List.of(Debt.Status.paid, Debt.Status.repacted, Debt.Status.disputed,
                Debt.Status.withdrawn)) {
            deuda.setStatus(estado);
            assertEquals(0, el(6, 10).enviados(), estado.name());
        }
    }

    @Test
    void una_campana_pausada_o_fuera_de_sus_fechas_no_contacta() {
        campana.setStatus(Campaign.Status.paused);
        when(campaigns.findByStatus(Campaign.Status.running)).thenReturn(List.of());
        assertEquals(0, el(6, 10).enviados());

        campana.setStatus(Campaign.Status.running);
        when(campaigns.findByStatus(Campaign.Status.running)).thenReturn(List.of(campana));
        campana.setEndsOn(LocalDate.of(2026, 10, 5));
        assertEquals(0, el(6, 10).enviados(), "termino ayer");
    }

    @Test
    void se_puede_pasar_por_una_sola_campana() {
        campana.setExternalId("APX-CMP-8");

        assertEquals(0, servicio.contactar(LocalDateTime.of(2026, 10, 6, 10, 0), "APX-CMP-9").enviados(),
                "las demas no se tocan");
        assertEquals(1, servicio.contactar(LocalDateTime.of(2026, 10, 6, 10, 0), "APX-CMP-8").enviados());
    }

    @Test
    void sin_correo_entre_sus_canales_no_contacta() {
        campana.setChannels("[\"whatsapp\"]");

        assertEquals(0, el(6, 10).enviados());
    }

    @Test
    void la_deuda_que_llego_antes_de_la_campana_cuenta_desde_el_inicio() {
        campana.setStartsOn(LocalDate.of(2026, 10, 8));

        assertEquals(0, el(6, 10).enviados());
        assertEquals(1, el(9, 10).enviados(), "dia 1 de la campana");
        ArgumentCaptor<AuthClient.PedidoDeCodigo> pedido = ArgumentCaptor.forClass(AuthClient.PedidoDeCodigo.class);
        verify(auth).emitirCodigo(pedido.capture());
        assertEquals("felipe.rojas@correo.cl", pedido.getValue().correo());
    }

    // ------------------------------------------------------------------
    //  Los feriados de la configuracion
    // ------------------------------------------------------------------

    /**
     * Con los feriados tal como los lee Spring Boot de application.properties
     * (con el mismo cargador, que une las lineas que terminan en \), mas lo
     * que sume CONTACTO_FERIADOS.
     */
    private ContactoService conLaConfiguracion(String contactoFeriados) throws IOException {
        Object feriados = new PropertiesPropertySourceLoader()
                .load("configuracion", new ClassPathResource("application.properties"))
                .getFirst()
                .getProperty("app.contacto.feriados");
        String conLaVariable = String.valueOf(feriados).replace("${CONTACTO_FERIADOS:}", contactoFeriados);
        return new ContactoService(campaigns, debts, events, auth, limite, new ObjectMapper(), conLaVariable);
    }

    @Test
    void en_los_feriados_de_2027_no_se_contacta() throws IOException {
        ContactoService conFeriados = conLaConfiguracion("");
        List<LocalDate> feriados = Stream.of("2027-01-01", "2027-03-26", "2027-03-27", "2027-05-01",
                        "2027-05-21", "2027-06-21", "2027-06-28", "2027-07-16", "2027-08-15", "2027-09-18",
                        "2027-09-19", "2027-10-11", "2027-10-31", "2027-11-01", "2027-12-08", "2027-12-25")
                .map(LocalDate::parse)
                .toList();

        for (LocalDate dia : feriados) {
            assertFalse(conFeriados.horaDeContacto(dia.atTime(10, 0)), dia + " es feriado");
        }
        assertTrue(conFeriados.horaDeContacto(LocalDateTime.of(2027, 6, 29, 10, 0)),
                "San Pedro y San Pablo cae martes y se corre al lunes 28");
        assertTrue(conFeriados.horaDeContacto(LocalDateTime.of(2027, 10, 12, 10, 0)),
                "el Encuentro de Dos Mundos cae martes y se corre al lunes 11");
        assertTrue(conFeriados.horaDeContacto(LocalDateTime.of(2027, 1, 4, 10, 0)), "un lunes cualquiera");
    }

    @Test
    void un_feriado_decretado_se_suma_sin_borrar_los_de_la_lista() throws IOException {
        ContactoService conFeriados = conLaConfiguracion("2027-11-22");

        assertFalse(conFeriados.horaDeContacto(LocalDateTime.of(2027, 11, 22, 10, 0)), "el decretado");
        assertFalse(conFeriados.horaDeContacto(LocalDateTime.of(2026, 10, 12, 10, 0)), "los de 2026 siguen");
        assertFalse(conFeriados.horaDeContacto(LocalDateTime.of(2027, 1, 1, 10, 0)), "y los de 2027");
    }

    @Test
    void avisa_cuando_faltan_los_feriados_de_un_ano() throws IOException {
        ContactoService conFeriados = conLaConfiguracion("");

        assertEquals(List.of(), conFeriados.anosSinFeriados(LocalDate.of(2026, 12, 15)), "2026 y 2027 estan");
        assertEquals(List.of(), conFeriados.anosSinFeriados(LocalDate.of(2027, 11, 30)));
        assertEquals(List.of(2028), conFeriados.anosSinFeriados(LocalDate.of(2027, 12, 1)),
                "desde diciembre, tambien el ano que viene");
        assertEquals(List.of(2028), conFeriados.anosSinFeriados(LocalDate.of(2028, 3, 2)));
    }
}
