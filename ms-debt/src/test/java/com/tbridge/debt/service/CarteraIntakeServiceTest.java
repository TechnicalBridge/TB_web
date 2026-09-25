package com.tbridge.debt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.debt.dto.response.CarteraResponse;
import com.tbridge.debt.dto.response.CarteraResponse.ResultadoDeuda;
import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.BatchRepository;
import com.tbridge.debt.repository.CampaignRepository;
import com.tbridge.debt.repository.DebtChargeRepository;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.DebtRepository;
import com.tbridge.debt.repository.DebtorRepository;
import com.tbridge.debt.repository.InstallmentRepository;
import com.tbridge.debt.repository.MandateRepository;
import com.tbridge.debt.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El alcance de DataBridge: solo deudores morosos. Una deuda entra con al
 * menos dos meses impagos; una que ya esta en gestion puede volver con menos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CarteraIntakeServiceTest {

    @Mock private OrganizationRepository organizations;
    @Mock private MandateRepository mandates;
    @Mock private CampaignRepository campaigns;
    @Mock private BatchRepository batches;
    @Mock private DebtorRepository debtors;
    @Mock private DebtRepository debts;
    @Mock private DebtChargeRepository charges;
    @Mock private InstallmentRepository installments;
    @Mock private DebtEventRepository events;
    @Mock private EventosService eventos;

    private final ObjectMapper json = new ObjectMapper();
    private CarteraIntakeService servicio;
    private Organization patrimonio;

    @BeforeEach
    void preparar() {
        servicio = new CarteraIntakeService(organizations, mandates, campaigns, batches, debtors, debts,
                charges, installments, events, json, eventos, 2);

        //  Patrimonio entrega lo suyo: sin agencia, no hace falta mandato.
        patrimonio = new Organization();
        patrimonio.setId(1L);
        patrimonio.setRut("76418902-7");
        patrimonio.setTradeName("Patrimonio Inmuebles");
        when(organizations.findByRut("76418902-7")).thenReturn(Optional.of(patrimonio));
        when(batches.findBySenderAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(debts.findByCreditorAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(debtors.findByRut(any())).thenReturn(Optional.empty());
        when(debtors.save(any(Debtor.class))).thenAnswer(llamada -> llamada.getArgument(0));
    }

    private JsonNode cartera(String... cargos) throws Exception {
        return json.readTree("""
                {"version": "1.0",
                 "lote": {"id_externo": "PAT-2026-09-18-01", "fecha_corte": "2026-09-18",
                          "acreedor": {"rut": "76418902-7"}},
                 "deudas": [{"id_externo": "CTR-2026-031", "moneda": "CLP", "concepto": "Arriendo mensual",
                             "deudor": {"rut": "18905214-6", "tipo": "persona",
                                        "nombre": "Valentina Soto Pizarro",
                                        "correo": "valentina.soto@correo.cl"},
                             "cargos": [%s]}]}
                """.formatted(String.join(",", cargos)));
    }

    private static String cargo(String concepto, String periodo, String vence) {
        return """
                {"concepto": "%s", "periodo": "%s", "monto": 410000, "fecha_vencimiento": "%s"}"""
                .formatted(concepto, periodo, vence);
    }

    private ResultadoDeuda recibir(JsonNode payload) {
        CarteraResponse respuesta = servicio.recibir(patrimonio, payload, Batch.Source.api);
        return respuesta.resultados().getFirst();
    }

    @Test
    void con_un_solo_mes_impago_todavia_no_es_morosa_y_no_entra() throws Exception {
        ResultadoDeuda resultado = recibir(cartera(cargo("Arriendo septiembre", "2026-09", "2026-09-05")));

        assertEquals("rechazada", resultado.resultado());
        assertEquals("bajo_umbral_mora", resultado.errores().getFirst().codigo());
        verify(debts, never()).save(any());
    }

    @Test
    void con_dos_meses_impagos_entra_a_cobranza() throws Exception {
        ResultadoDeuda resultado = recibir(cartera(
                cargo("Arriendo agosto", "2026-08", "2026-08-05"),
                cargo("Arriendo septiembre", "2026-09", "2026-09-05")));

        assertEquals("registrada", resultado.resultado());
        assertEquals(44L, resultado.moraDias());
    }

    @Test
    void el_arriendo_y_el_gasto_comun_del_mismo_mes_son_un_solo_mes() throws Exception {
        ResultadoDeuda resultado = recibir(cartera(
                cargo("Arriendo septiembre", "2026-09", "2026-09-05"),
                cargo("Gasto comun septiembre", "2026-09", "2026-09-10")));

        assertEquals("rechazada", resultado.resultado());
        assertEquals("bajo_umbral_mora", resultado.errores().getFirst().codigo());
    }

    @Test
    void una_deuda_en_gestion_puede_volver_con_un_solo_mes() throws Exception {
        //  El arrendatario pago agosto en la oficina y Patrimonio manda el
        //  saldo menor. Rechazarlo dejaria a DataBridge cobrando lo que ya no
        //  se debe.
        Debt enGestion = new Debt();
        enGestion.setId(3L);
        enGestion.setCreditor(patrimonio);
        enGestion.setExternalId("CTR-2026-031");
        when(debts.findByCreditorAndExternalId(patrimonio, "CTR-2026-031")).thenReturn(Optional.of(enGestion));

        ResultadoDeuda resultado = recibir(cartera(cargo("Arriendo septiembre", "2026-09", "2026-09-05")));

        assertEquals("actualizada", resultado.resultado());
    }

    @Test
    void un_umbral_menor_que_uno_no_deja_arrancar_el_servicio() {
        assertThrows(IllegalStateException.class, () -> new CarteraIntakeService(organizations, mandates,
                campaigns, batches, debtors, debts, charges, installments, events, json, eventos, 0));
    }
}
