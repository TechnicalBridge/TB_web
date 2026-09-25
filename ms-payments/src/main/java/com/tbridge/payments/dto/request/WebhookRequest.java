package com.tbridge.payments.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El aviso de una pasarela.
 *
 * <p>Cada pasarela manda su propio formato; de todo lo que traiga, estos son
 * los campos que se leen. La firma puede venir en la cabecera
 * {@code X-Signature} o en el cuerpo.
 *
 * <p>Es el unico cuerpo de ms-payments que tolera campos desconocidos: lo
 * escribe un tercero, y rechazar su aviso porque agrego un campo dejaria un
 * pago cobrado sin registrar.
 */
@Schema(description = "El aviso de pago de la pasarela")
@JsonIgnoreProperties(ignoreUnknown = true)
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
