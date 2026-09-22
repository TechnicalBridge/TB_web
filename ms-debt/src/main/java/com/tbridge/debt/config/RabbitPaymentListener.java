package com.tbridge.debt.config;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.debt.service.DebtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "events.rabbit", havingValue = "true")
public class RabbitPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitPaymentListener.class);

    private final DebtService debts;

    public RabbitPaymentListener(DebtService debts) {
        this.debts = debts;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onPagoConfirmado(PagoConfirmado aviso) {
        log.info("RabbitMQ pago.confirmado deuda={} pago={}", aviso.debtId(), aviso.paymentId());
        debts.onPagoConfirmado(aviso);
    }
}
