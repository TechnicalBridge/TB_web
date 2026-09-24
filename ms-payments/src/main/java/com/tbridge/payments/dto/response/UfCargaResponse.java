package com.tbridge.payments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Como resulto una carga desde el Banco Central. Nunca es un error HTTP: una
 * carga fallida se reintenta al dia siguiente, y el motivo va en {@code error}.
 */
@Schema(description = "Cuantos dias se cargaron y hasta cual, o por que no se pudo")
public record UfCargaResponse(
        @Schema(nullable = true, example = "47") Integer cargados,
        @Schema(nullable = true, example = "2026-10-31") LocalDate hasta,
        @Schema(nullable = true, example = "El Banco Central respondio codigo -5: Invalid username or password")
        String error
) {

    public static UfCargaResponse exito(int cargados, LocalDate hasta) {
        return new UfCargaResponse(cargados, hasta, null);
    }

    public static UfCargaResponse fallo(String motivo) {
        return new UfCargaResponse(null, null, motivo);
    }
}
