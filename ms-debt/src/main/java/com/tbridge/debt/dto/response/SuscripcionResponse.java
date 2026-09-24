package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La suscripcion a los eventos: a donde se avisa y con que secreto se firma.
 *
 * <p>Registrar la misma URL otra vez devuelve el mismo secreto, asi el
 * receptor puede repetir la llamada sin invalidar lo que ya configuro.
 */
@Schema(description = "La URL registrada, los eventos que recibe y el secreto de la firma")
public record SuscripcionResponse(
        @Schema(example = "https://apofyx.example/api/v1/eventos") String url,
        @Schema(description = "La lista de eventos, o \"todos\"", example = "[\"pago.confirmado\",\"deuda.saldada\"]")
        Object eventos,
        @Schema(description = "Con el se verifica X-Firma. Guardarlo como secreto", example = "whsec_...")
        String secreto,
        @Schema(example = "X-Firma: v1=HMAC-SHA256(secreto, X-Timestamp + \".\" + cuerpo), en hex")
        String firma
) {
}
