package com.tbridge.payments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El aviso de una pasarela.
 *
 * <p>Cada pasarela manda su propio formato; de todo lo que traiga, estos son
 * los campos que se leen. La firma puede venir en la cabecera
 * {@code X-Signature} o en el cuerpo.
 */
@Schema(description = "El aviso de pago de la pasarela")
public record WebhookRequest(
        @Schema(description = "El id del pago en DataBridge", example = "41") Long paymentId,
        @Schema(description = "Lo mismo, con el nombre que usan algunas pasarelas", example = "41") Long id,
        @Schema(description = "El id de la transaccion en la pasarela", example = "wp-9f31c2") String txnId,
        @Schema(description = "HMAC del pago, si no viene en la cabecera X-Signature") String signature
) {

    public Long pago() {
        return paymentId != null ? paymentId : id;
    }
}
