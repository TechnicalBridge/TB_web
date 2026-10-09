package com.tbridge.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Como se nota el deudor en su ultimo mensaje. */
public record Animo(
        @Schema(description = "desconfianza, frustracion, positivo o neutral", example = "frustracion")
        String etiqueta,
        @Schema(description = "Cuantas senales se encontraron", example = "1")
        int intensidad) {
}
