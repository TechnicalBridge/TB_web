package com.tbridge.debt.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.debt.client.AuthClient;
import com.tbridge.debt.dto.response.RecordatoriosResponse;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtEvent;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Installment;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.InstallmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El recordatorio sale una vez por cuota, y solo a quien lo quiere y tiene correo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatorioServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 17);

    @Mock private InstallmentRepository installments;
    @Mock private DebtEventRepository events;
    @Mock private AuthClient auth;

    private RecordatorioService servicio;
    private Installment cuota;
    private Debtor deudor;

    @BeforeEach
    void preparar() {
        servicio = new RecordatorioService(installments, events, auth, 3);
        Organization patrimonio = new Organization();
        patrimonio.setTradeName("Patrimonio Inmuebles");
        deudor = new Debtor();
        deudor.setRut("16482337-7");
        deudor.setEmail("felipe.rojas@correo.cl");
        Debt deuda = new Debt();
        deuda.setCreditor(patrimonio);
        deuda.setDebtor(deudor);
        deuda.setExternalId("CTR-2025-014");
        cuota = new Installment();
        cuota.setId(34L);
        cuota.setDebt(deuda);
        cuota.setDueDate(LocalDate.of(2026, 10, 20));
        cuota.setAmount(new BigDecimal("173333"));
        when(installments.porRecordar(Installment.Status.pending, HOY, HOY.plusDays(3),
                List.of(Debt.Status.open, Debt.Status.repacted))).thenReturn(List.of(cuota));
    }

    @Test
    void la_cuota_por_vencer_se_recuerda_con_su_fecha_y_queda_marcada() {
        RecordatoriosResponse pasada = servicio.enviar(HOY);

        ArgumentCaptor<AuthClient.PedidoDeCodigo> pedido = ArgumentCaptor.forClass(AuthClient.PedidoDeCodigo.class);
        verify(auth).emitirCodigo(pedido.capture());
        assertEquals(LocalDate.of(2026, 10, 20), pedido.getValue().vence());
        assertEquals("felipe.rojas@correo.cl", pedido.getValue().correo());
        assertEquals("Patrimonio Inmuebles", pedido.getValue().acreedor());
        assertNotNull(cuota.getRemindedAt());
        verify(events).save(any(DebtEvent.class));
        assertEquals(1, pasada.enviados());
    }

    @Test
    void quien_apago_los_recordatorios_o_no_tiene_correo_no_recibe_nada() {
        deudor.setReminders(false);
        assertEquals(1, servicio.enviar(HOY).omitidos());

        deudor.setReminders(true);
        deudor.setEmail(null);
        assertEquals(1, servicio.enviar(HOY).omitidos());

        verify(auth, never()).emitirCodigo(any());
        assertNull(cuota.getRemindedAt());
    }

    @Test
    void si_ms_auth_no_responde_la_cuota_queda_para_la_pasada_siguiente() {
        when(auth.emitirCodigo(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "sin ms-auth"));

        RecordatoriosResponse pasada = servicio.enviar(HOY);

        assertEquals(0, pasada.enviados());
        assertEquals(1, pasada.omitidos());
        assertNull(cuota.getRemindedAt());
        verify(installments, never()).save(any());
    }
}
