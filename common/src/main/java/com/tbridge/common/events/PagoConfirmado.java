package com.tbridge.common.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * El aviso que ms-payments le manda a ms-debt cuando un pago se concreta.
 *
 * <p>El deudor va por RUT —el correo cambia, el RUT no— y viaja lo cobrado en
 * pesos junto con el valor de la UF, para que ms-debt no tenga que volver a
 * convertir ni adivinar con que valor se hizo.
 *
 * <p>Vive en common porque lo escriben dos servicios: el que lo emite y el que
 * lo recibe tienen que estar de acuerdo en la forma.
 */
public record PagoConfirmado(
        String tipo,
        Long paymentId,
        Long debtId,
        Long installmentId,
        String debtorRut,
        String creditorRut,
        BigDecimal amount,
        String currency,
        Long amountClp,
        BigDecimal ufValue,
        String gateway,
        String gatewayTxnId,
        Instant paidAt
) {
    public static final String TIPO = "pago.confirmado";

    /*
     * Como viaja por RabbitMQ. Los dos servicios leen estas constantes: cuando
     * cada uno tenia las suyas, ms-payments publicaba con "pago.confirmado",
     * ms-debt escuchaba "pago.exitoso", y RabbitMQ descartaba cada pago en
     * silencio porque no calzaba con ninguna cola.
     */
    public static final String EXCHANGE = "tbridge.pagos";
    public static final String ROUTING_KEY = "pago.confirmado";
    public static final String QUEUE = "ms-debt.pagos-confirmados";
}
