package com.tbridge.debt.service;

import com.tbridge.common.events.PagoConfirmado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Los avisos de pago que llegan por RabbitMQ, cuando EVENTS_RABBIT=true.
 *
 * <p>Sin RabbitMQ, el mismo aviso llega por HTTP a
 * {@code POST /internal/events/pago-confirmado}. Los dos caminos terminan en
 * {@link DebtService#onPagoConfirmado}, que ignora un aviso repetido.
 */
@Component
@ConditionalOnProperty(name = "events.rabbit", havingValue = "true")
public class RabbitPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitPaymentListener.class);

    private final DebtService debts;

    public RabbitPaymentListener(DebtService debts) {
        this.debts = debts;
    }

    @RabbitListener(queues = PagoConfirmado.QUEUE)
    public void onPagoConfirmado(PagoConfirmado aviso) {
        log.info("RabbitMQ pago.confirmado deuda={} pago={}", aviso.debtId(), aviso.paymentId());
        debts.onPagoConfirmado(aviso);
    }
}
