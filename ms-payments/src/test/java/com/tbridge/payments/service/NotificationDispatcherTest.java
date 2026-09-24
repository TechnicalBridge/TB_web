package com.tbridge.payments.service;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.payments.model.DebtNotification;
import com.tbridge.payments.model.Payment;
import com.tbridge.payments.repository.DebtNotificationRepository;
import com.tbridge.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El aviso a ms-debt sale despues del cobro y se reintenta: si ms-debt esta
 * caido, el pago no se pierde, espera.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private DebtNotificationRepository avisos;
    @Mock private PaymentRepository payments;
    @Mock private EventPublisher publicador;

    @InjectMocks
    private NotificationDispatcher despachador;

    private DebtNotification pendiente() {
        DebtNotification aviso = DebtNotification.para(41L);
        when(avisos.findByStatusAndNextAttemptAtBefore(any(), any())).thenReturn(List.of(aviso));
        Payment pago = new Payment();
        pago.setId(41L);
        pago.setDebtId(3L);
        pago.setDebtorRut("16482337-7");
        pago.setCreditorRut("76418902-7");
        pago.setAmount(new BigDecimal("410000"));
        pago.setAmountClp(410000L);
        pago.setCurrency(Payment.Currency.CLP);
        pago.setGateway(Payment.Gateway.webpay);
        pago.setGatewayTxnId("wp-1");
        pago.setPaidAt(Instant.now());
        when(payments.findById(41L)).thenReturn(Optional.of(pago));
        return aviso;
    }

    @Test
    void entregado_queda_marcado_con_el_aviso_completo() {
        DebtNotification aviso = pendiente();

        despachador.despachar();

        ArgumentCaptor<PagoConfirmado> enviado = ArgumentCaptor.forClass(PagoConfirmado.class);
        verify(publicador).publicar(enviado.capture());
        assertEquals(3L, enviado.getValue().debtId());
        assertEquals("16482337-7", enviado.getValue().debtorRut());
        assertEquals(410000L, enviado.getValue().amountClp());
        assertEquals(DebtNotification.Status.delivered, aviso.getStatus());
    }

    @Test
    void si_ms_debt_esta_caido_se_reintenta_despues() {
        DebtNotification aviso = pendiente();
        doThrow(new IllegalStateException("Connection refused")).when(publicador).publicar(any());

        despachador.despachar();

        assertEquals(DebtNotification.Status.pending, aviso.getStatus());
        assertEquals((short) 1, aviso.getAttempts());
        assertEquals("Connection refused", aviso.getLastError());
        verify(avisos).save(aviso);
    }
}
