package com.tbridge.debt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.dto.evento.CampanaAvanceDatos;
import com.tbridge.debt.dto.request.CampanaRequest;
import com.tbridge.debt.dto.response.CampanaPortalResponse;
import com.tbridge.debt.exception.CarteraInvalida;
import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las campanas desde el portal: con las reglas del contrato, sin reglas propias. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampanaServiceTest {

    @Mock private CampaignRepository campanas;
    @Mock private MandatoService mandatos;
    @Mock private CampanaAvanceService avance;

    private CampanaService servicio;
    private Organization apofyx;
    private Campaign campana;

    @BeforeEach
    void preparar() {
        servicio = new CampanaService(campanas, mandatos, avance, new ObjectMapper());
        apofyx = organizacion(2L, "77305118-6", "APOFYX");
        campana = new Campaign();
        campana.setAgency(apofyx);
        campana.setCreditor(organizacion(1L, "76418902-7", "Patrimonio Inmuebles"));
        campana.setExternalId("APX-CMP-8");
        campana.setName("Arriendos octubre");
        campana.setStartsOn(LocalDate.of(2026, 10, 1));
        campana.setChannels("[\"correo\"]");
        campana.setAttempts((short) 3);
        campana.setCadenceDays("[1, 4, 11]");
        campana.setStatus(Campaign.Status.paused);
        when(campanas.findByAgencyAndExternalId(eq(apofyx), anyString())).thenReturn(Optional.of(campana));
        when(avance.avance(campana)).thenReturn(
                new CampanaAvanceDatos("APX-CMP-8", "2026-10-08", 12, 20, 9, 2, 4, 3, 1, 0, 1230000L, null));
    }

    private static Organization organizacion(long id, String rut, String nombre) {
        Organization o = new Organization();
        o.setId(id);
        o.setRut(rut);
        o.setTradeName(nombre);
        return o;
    }

    @Test
    void la_lista_trae_cada_campana_con_su_avance() {
        when(campanas.findByAgencyOrderByStartsOnDesc(apofyx)).thenReturn(List.of(campana));

        CampanaPortalResponse vista = servicio.listar(apofyx).getFirst();

        assertEquals("APX-CMP-8", vista.idExterno());
        assertEquals("Patrimonio Inmuebles", vista.acreedor());
        assertFalse(vista.propia());
        assertEquals(List.of("correo"), vista.canales());
        assertEquals(List.of(1, 4, 11), vista.cadenciaDias());
        assertEquals("pausada", vista.estado());
        assertEquals(12, vista.deudas());
        assertEquals(20, vista.contactos());
        assertEquals(4, vista.pagos());
    }

    @Test
    void sin_cadencia_muestra_la_que_databridge_cumple() {
        campana.setCadenceDays(null);
        when(campanas.findByAgencyOrderByStartsOnDesc(apofyx)).thenReturn(List.of(campana));

        assertEquals(List.of(1, 4, 11, 25, 45), servicio.listar(apofyx).getFirst().cadenciaDias());
    }

    @Test
    void las_terminadas_van_al_final() {
        Campaign terminada = new Campaign();
        terminada.setAgency(apofyx);
        terminada.setCreditor(campana.getCreditor());
        terminada.setExternalId("APX-CMP-9");
        terminada.setStartsOn(LocalDate.of(2026, 10, 5));
        terminada.setStatus(Campaign.Status.finished);
        when(avance.avance(terminada)).thenReturn(
                new CampanaAvanceDatos("APX-CMP-9", "2026-10-08", 0, 0, 0, 0, 0, 0, 0, 0, 0L, null));
        when(campanas.findByAgencyOrderByStartsOnDesc(apofyx)).thenReturn(List.of(terminada, campana));

        assertEquals(List.of("APX-CMP-8", "APX-CMP-9"),
                servicio.listar(apofyx).stream().map(CampanaPortalResponse::idExterno).toList());
    }

    @Test
    void sin_id_externo_databridge_le_pone_uno() {
        servicio.guardar(apofyx, new CampanaRequest(null, "76418902-7", "Nueva", "2026-10-08", null, null, 2, null, null));

        ArgumentCaptor<CampanaRequest> enviado = ArgumentCaptor.forClass(CampanaRequest.class);
        verify(mandatos).registrarCampana(eq(apofyx), enviado.capture());
        assertTrue(enviado.getValue().idExterno().matches("CMP-\\d{8}-[A-HJ-NP-Z2-9]{4}"), enviado.getValue().idExterno());
        assertEquals("Nueva", enviado.getValue().nombre());
    }

    @Test
    void cambiar_el_estado_manda_solo_el_estado() {
        servicio.cambiarEstado(apofyx, "APX-CMP-8", "en_curso");

        ArgumentCaptor<CampanaRequest> enviado = ArgumentCaptor.forClass(CampanaRequest.class);
        verify(mandatos).registrarCampana(eq(apofyx), enviado.capture());
        assertEquals("en_curso", enviado.getValue().estado());
        assertEquals("76418902-7", enviado.getValue().acreedorRut());
        assertNull(enviado.getValue().nombre());
        assertNull(enviado.getValue().inicio());
    }

    @Test
    void la_campana_de_otra_empresa_no_se_encuentra() {
        when(campanas.findByAgencyAndExternalId(apofyx, "AJENA")).thenReturn(Optional.empty());

        CarteraInvalida fallo = assertThrows(CarteraInvalida.class,
                () -> servicio.cambiarEstado(apofyx, "AJENA", "pausada"));

        assertEquals(404, fallo.getStatus());
        verify(mandatos, never()).registrarCampana(any(), any());
    }
}
