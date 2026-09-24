package com.tbridge.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * El codigo recien emitido. Es la unica vez que existe en claro: en la base
 * queda solo su huella.
 */
@Schema(description = "El codigo emitido, que quien lo pidio le hace llegar al deudor")
public record CodigoEmitidoResponse(
        @Schema(example = "K7M2QX") String codigo,
        @Schema(example = "16482337-7") String rut,
        @Schema(example = "[\"correo\"]") List<String> canales,
        @Schema(example = "2026-09-25T15:04:05Z") Instant expiraEn,
        @Schema(example = "Entra a http://localhost:8080 y escribe tu RUT y el codigo") String comoSeUsa
) {
}
