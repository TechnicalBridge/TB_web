package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Cuanto se debe y a quien, para ms-payments.
 *
 * <p>ms-payments cobra este monto y no el que manda el navegador: quien supiera
 * el id de una deuda podria pagar un peso y darla por saldada.
 */
@Schema(description = "El monto a cobrar: el saldo, o las cuotas pedidas")
public record DebtSnapshotResponse(
        @Schema(example = "3") Long debtId,
        @Schema(example = "76418902-7") String creditorRut,
        @Schema(example = "76991245-2") String debtorRut,
        @Schema(example = "UF") String currency,
        @Schema(example = "96.25") BigDecimal amount,
        @Schema(description = "La cuota, cuando se paga una sola. Con varias va vacia y el pago se imputa "
                + "de la mas antigua a la mas nueva", nullable = true, example = "12") Long installmentId
) {
}
