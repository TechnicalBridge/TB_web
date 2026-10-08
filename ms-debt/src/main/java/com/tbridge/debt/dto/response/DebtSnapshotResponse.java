package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

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
                + "de la mas antigua a la mas nueva", nullable = true, example = "12") Long installmentId,
        @Schema(description = "Las cuotas que cubre el monto, de la que vence primero en adelante. Con esto "
                + "ms-payments sabe si dos pagos cubren la misma cuota", example = "[12, 13]")
        List<Long> installmentIds,
        @Schema(description = "Lo que el monto tiene de capital: las cuotas", example = "92.50") BigDecimal capital,
        @Schema(description = "Lo que el monto tiene de mora, si la deuda genera intereses, ya sin el descuento. "
                + "Cero sin tasa", example = "3.75") BigDecimal interes,
        @Schema(description = "La mora que se condona por pronto pago: solo si se paga toda la deuda durante una "
                + "campana con descuento. El monto ya viene sin ella. Cero sin descuento", example = "0")
        BigDecimal descuento
) {
}
