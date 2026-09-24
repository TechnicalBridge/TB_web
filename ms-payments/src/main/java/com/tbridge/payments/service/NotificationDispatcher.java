package com.tbridge.payments.service;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.payments.model.DebtNotification;
import com.tbridge.payments.model.Payment;
import com.tbridge.payments.repository.DebtNotificationRepository;
import com.tbridge.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Saca los avisos de la bandeja y los entrega.
 *
 * <p><b>Por que separado del cobro.</b> Si el aviso se mandara dentro de la
 * transaccion que confirma el pago, una caida de ms-debt dejaria el dinero
 * cobrado y la deuda sin enterarse. Aca el pago se guarda con su aviso
 * pendiente y este proceso lo entrega despues; si el otro lado esta caido, se
 * reintenta hasta 24 horas y despues queda visible para reenviarlo a mano.
 *
 * <p>Entregar "al menos una vez" significa que ms-debt puede recibir el mismo
 * aviso dos veces, y por eso el contrato exige que deduplique por id.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final DebtNotificationRepository avisos;
    private final PaymentRepository payments;
    private final EventPublisher publisher;

    public NotificationDispatcher(
            DebtNotificationRepository avisos,
            PaymentRepository payments,
            EventPublisher publisher
    ) {
        this.avisos = avisos;
        this.payments = payments;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.dispatch-interval-ms:15000}")
    @Transactional
    public void despachar() {
        List<DebtNotification> pendientes = avisos.findByStatusAndNextAttemptAtBefore(
                DebtNotification.Status.pending, Instant.now());

        for (DebtNotification aviso : pendientes) {
            Payment pago = payments.findById(aviso.getPaymentId()).orElse(null);
            if (pago == null) {
                aviso.fallo("El pago ya no existe");
                avisos.save(aviso);
                continue;
            }
            try {
                publisher.publicar(construir(pago));
                aviso.entregado();
                log.info("Aviso de pago {} entregado a ms-debt", pago.getId());
            } catch (Exception fallo) {
                aviso.fallo(fallo.getMessage());
                log.warn("No se pudo avisar del pago {} (intento {}): {}",
                        pago.getId(), aviso.getAttempts(), fallo.getMessage());
            }
            avisos.save(aviso);
        }
    }

    private PagoConfirmado construir(Payment pago) {
        return new PagoConfirmado(
                PagoConfirmado.TIPO,
                pago.getId(),
                pago.getDebtId(),
                pago.getInstallmentId(),
                pago.getDebtorRut(),
                pago.getCreditorRut(),
                pago.getAmount(),
                pago.getCurrency().name(),
                pago.getAmountClp(),
                pago.getUfValue(),
                pago.getGateway().name(),
                pago.getGatewayTxnId(),
                pago.getPaidAt()
        );
    }
}
