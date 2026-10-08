package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * {@code pago.confirmado}: lo que el acreedor necesita para imputar el pago en
 * su propio sistema.
 *
 * <p>Los pesos viajan enteros y la UF con sus decimales, por eso {@code monto}
 * es un numero sin tipo fijo. En UF va el valor usado: la UF cambia todos los
 * dias, y sin ese dato nadie podria reconstruir por que UF 38,5 fueron esos
 * pesos. En pesos, {@code valor_uf} no viene.
 *
 * <p>{@code monto} es todo lo que se cobro. {@code capital} es lo que se imputa
 * a los cargos, y {@code interes} la mora de esas cuotas, si la deuda genera
 * intereses: el acreedor la registra aparte. {@code descuento} es la mora que se
 * condono por pronto pago (contrato §7.1); {@code interes} ya viene sin ella.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PagoConfirmadoDatos(
        String deudaIdExterno,
        String pagoId,
        Number monto,
        String moneda,
        Long montoClp,
        BigDecimal valorUf,
        String medio,
        String pagadoEn,
        Number capital,
        Number interes,
        Number descuento
) {
}
