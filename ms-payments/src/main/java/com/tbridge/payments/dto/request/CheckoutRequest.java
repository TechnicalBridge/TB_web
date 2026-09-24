package com.tbridge.payments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Que se quiere pagar y por donde.
 *
 * <p>No trae monto a proposito: el monto lo dice ms-debt. Si lo mandara el
 * navegador, bastaria editar la peticion para pagar un peso.
 */
@Schema(description = "La deuda (o la cuota) a pagar y la pasarela")
public record CheckoutRequest(
        @Schema(example = "3")
        @NotNull(message = "Indica que deuda quieres pagar")
        @Positive(message = "Indica que deuda quieres pagar")
        Long debtId,

        @Schema(description = "Una cuota en particular. Sin ella se paga todo el saldo", example = "12", nullable = true)
        @Positive(message = "Esa cuota no existe")
        Long installmentId,

        @Schema(allowableValues = {"webpay", "mercadopago", "khipu"}, example = "webpay")
        @NotBlank(message = "Pasarela no soportada (Mercado Pago, Khipu o Webpay)")
        @Pattern(regexp = "(?i)webpay|mercadopago|khipu", message = "Pasarela no soportada (Mercado Pago, Khipu o Webpay)")
        String gateway
) {
}
