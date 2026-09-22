package com.tbridge.payments.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * El aviso que ms-payments le manda a ms-debt cuando un pago se concreta.
 *
 * <p>Reemplaza a {@code PagoExitosoEvent} del modulo common, que identificaba
 * al deudor por correo y llevaba los ids como texto. Aca el deudor va por RUT
 * —el correo cambia, el RUT no— y ademas viaja lo cobrado en pesos junto con
 * el valor de la UF, para que ms-debt no tenga que volver a convertir ni
 * adivinar con que valor se hizo.
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
}
