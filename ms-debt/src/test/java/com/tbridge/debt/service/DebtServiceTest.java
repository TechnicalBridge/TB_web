package com.tbridge.debt.service;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.common.exception.ApiException;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.dto.response.DebtSnapshotResponse;
import com.tbridge.debt.dto.response.DebtSummaryResponse;
import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtEvent;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Installment;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.DebtChargeRepository;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.DebtRepository;
import com.tbridge.debt.repository.DebtorRepository;
import com.tbridge.debt.repository.InstallmentRepository;
import com.tbridge.debt.repository.OrganizationRepository;
import com.tbridge.debt.repository.RepactationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas de las deudas: cada quien ve lo suyo, el saldo sale de las
 * cuotas, y un pago avisado dos veces se abona una.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebtServiceTest {

    @Mock private DebtRepository debts;
    @Mock private DebtorRepository debtors;
    @Mock private OrganizationRepository organizations;
    @Mock private DebtChargeRepository charges;
    @Mock private InstallmentRepository installments;
    @Mock private RepactationRepository repactations;
    @Mock private DebtEventRepository events;
    @Mock private EventosService eventos;
    @Spy private RepactationService repactation = new RepactationService();

    @InjectMocks
    private DebtService servicio;

    private Organization patrimonio;
    private Organization apofyx;
    private Debtor valentina;
    private Debt deuda;
    private final List<Installment> cuotas = new ArrayList<>();
    private final List<DebtEvent> historia = new ArrayList<>();

    private static Organization organizacion(long id, String rut, String nombre) {
        Organization org = new Organization();
        org.setId(id);
        org.setRut(rut);
        org.setTradeName(nombre);
        return org;
    }

    @BeforeEach
    void preparar() {
        patrimonio = organizacion(1L, "76418902-7", "Patrimonio Inmuebles");
        apofyx = organizacion(2L, "77305118-6", "APOFYX");
        valentina = new Debtor();
        valentina.setId(10L);
        valentina.setRut("18905214-6");
        valentina.setFullName("Valentina Soto Pizarro");

        Batch lote = new Batch();
        lote.setSender(apofyx);
        lote.setExternalId("APX-2026-09-19-004");
        deuda = new Debt();
        deuda.setId(3L);
        deuda.setCreditor(patrimonio);
        deuda.setDebtor(valentina);
        deuda.setLastBatch(lote);
        deuda.setExternalId("CTR-2026-031");
        deuda.setConcept("Arriendo mensual");
        deuda.setCurrency(Debt.Currency.CLP);
        deuda.setOriginalAmount(new BigDecimal("410000"));

        Installment unica = new Installment();
        unica.setId(12L);
        unica.setDebt(deuda);
        unica.setNumber((short) 1);
        unica.setDueDate(LocalDate.of(2026, 9, 5));
        unica.setAmount(new BigDecimal("410000"));
        cuotas.add(unica);

        when(debts.findById(3L)).thenReturn(Optional.of(deuda));
        when(installments.findById(12L)).thenReturn(Optional.of(unica));
        when(installments.findByDebtAndStatus(eq(deuda), any())).thenAnswer(llamada -> cuotas.stream()
                .filter(c -> c.getStatus() == llamada.getArgument(1)).toList());
        when(events.findByDebtOrderByOccurredAtAsc(deuda)).thenReturn(historia);
        when(events.save(any())).thenAnswer(llamada -> {
            historia.add(llamada.getArgument(0));
            return llamada.getArgument(0);
        });
        when(debtors.findByRut("18905214-6")).thenReturn(Optional.of(valentina));
        when(organizations.findByRut("77305118-6")).thenReturn(Optional.of(apofyx));
        when(organizations.findByRut("76418902-7")).thenReturn(Optional.of(patrimonio));
    }

    private static JwtPrincipal deudor(String rut) {
        return new JwtPrincipal(rut, null, "DEBTOR", null, rut);
    }

    private static JwtPrincipal empresa(String rut) {
        return new JwtPrincipal("1", "x@empresa.cl", "CREDITOR", "Alguien", rut);
    }

    @Test
    void el_deudor_ve_su_deuda_con_el_saldo_de_las_cuotas() {
        when(debts.findByDebtorOrderByUpdatedAtDesc(valentina)).thenReturn(List.of(deuda));

        List<DebtSummaryResponse> suyas = servicio.listFor(deudor("18905214-6"));

        assertEquals(1, suyas.size());
        assertEquals(new BigDecimal("410000"), suyas.getFirst().saldo());
        assertEquals(BigDecimal.ZERO, suyas.getFirst().pagado());
    }

    @Test
    void la_agencia_que_entrego_la_cartera_la_ve_y_otra_empresa_no() {
        //  APOFYX no es la acreedora, pero envio el lote: opera esa cartera.
        assertEquals(3L, servicio.requireVisible(empresa("77305118-6"), 3L).getId());

        Organization otra = organizacion(9L, "96512345-8", "Otra");
        when(organizations.findByRut("96512345-8")).thenReturn(Optional.of(otra));
        ApiException error = assertThrows(ApiException.class,
                () -> servicio.requireVisible(empresa("96512345-8"), 3L));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void un_deudor_no_ve_la_deuda_de_otro() {
        Debtor felipe = new Debtor();
        felipe.setId(11L);
        when(debtors.findByRut("16482337-7")).thenReturn(Optional.of(felipe));

        ApiException error = assertThrows(ApiException.class,
                () -> servicio.getFor(deudor("16482337-7"), 3L));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void una_sesion_de_empresa_sin_rut_no_ve_ninguna_cartera() {
        //  Devolver "todas" seria la fuga que este diseno cerro.
        ApiException error = assertThrows(ApiException.class,
                () -> servicio.listFor(new JwtPrincipal("1", "x@y.cl", "CREDITOR", "X", null)));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void ms_payments_cobra_el_saldo_y_la_cuota_que_vence_primero() {
        DebtSnapshotResponse snapshot = servicio.snapshotInterno(3L, null);

        assertEquals(new BigDecimal("410000"), snapshot.amount());
        assertEquals(12L, snapshot.installmentId());
        assertEquals("18905214-6", snapshot.debtorRut());
    }

    private PagoConfirmado aviso() {
        return new PagoConfirmado(PagoConfirmado.TIPO, 41L, 3L, null, "18905214-6", "76418902-7",
                new BigDecimal("410000"), "CLP", 410000L, null, "webpay", "wp-9f31c2", Instant.now());
    }

    @Test
    void un_pago_que_cubre_el_saldo_deja_la_deuda_pagada_y_avisa() {
        servicio.onPagoConfirmado(aviso());

        assertEquals(Installment.Status.paid, cuotas.getFirst().getStatus());
        assertEquals(Debt.Status.paid, deuda.getStatus());
        verify(eventos).publicar(eq(deuda), eq(EventosService.PAGO_CONFIRMADO), any(), any());
        verify(eventos).publicar(eq(deuda), eq(EventosService.DEUDA_SALDADA), any(), any());
    }

    @Test
    void el_mismo_aviso_dos_veces_se_abona_una_sola() {
        servicio.onPagoConfirmado(aviso());
        servicio.onPagoConfirmado(aviso());

        ArgumentCaptor<DebtEvent> anotados = ArgumentCaptor.forClass(DebtEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(anotados.capture());
        //  payment_applied y settled, de la primera vez. La segunda no anota nada.
        assertEquals(List.of(DebtEvent.Type.payment_applied, DebtEvent.Type.settled),
                anotados.getAllValues().stream().map(DebtEvent::getType).toList());
    }

    @Test
    void la_empresa_no_repacta_por_el_deudor() {
        ApiException error = assertThrows(ApiException.class,
                () -> servicio.applyRepact(empresa("77305118-6"), 3L, 6));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(installments, never()).save(any());
    }

    @Test
    void repactar_anula_las_cuotas_pendientes_en_vez_de_borrarlas() {
        when(installments.findByDebtOrderByNumberAsc(deuda)).thenReturn(cuotas);
        when(installments.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

        servicio.applyRepact(deudor("18905214-6"), 3L, 3);

        assertEquals(Installment.Status.void_, cuotas.getFirst().getStatus());
        assertEquals(Debt.Status.repacted, deuda.getStatus());
        verify(eventos).publicar(eq(deuda), eq(EventosService.REPACTACION_ACEPTADA), any(), any());
    }
}
