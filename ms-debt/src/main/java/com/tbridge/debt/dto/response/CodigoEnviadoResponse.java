package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * El codigo de acceso salio hacia el deudor.
 *
 * <p>El codigo no viene: quien lo pidio es el personal de la empresa, y si lo
 * viera podria entrar como el deudor. Solo se dice a donde se mando.
 */
@Schema(description = "A donde se mando el codigo, sin el codigo")
public record CodigoEnviadoResponse(
        @Schema(example = "true") boolean enviado,
        @Schema(example = "correo") String canal,
        @Schema(description = "Enmascarado: se reconoce sin exponerlo", example = "fe**********@correo.cl") String destino,
        @Schema(nullable = true) Instant expiraEn
) {
}
