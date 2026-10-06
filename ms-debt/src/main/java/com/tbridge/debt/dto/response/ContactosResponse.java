package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lo que hizo una pasada de las campanas. */
@Schema(description = "Cuantos toques de campana salieron y cuantos quedaron para despues")
public record ContactosResponse(
        @Schema(example = "3") int enviados,
        @Schema(description = "Tocaban, pero la ley no deja escribirle hoy al deudor, no tiene correo, o ms-auth "
                + "no respondio. Salen en una pasada siguiente", example = "1")
        int omitidos,
        @Schema(description = "Si era horario de cobranza: de lunes a sabado, de 8:00 a 20:00, sin feriados. "
                + "Fuera de el no sale nada", example = "true")
        boolean enHorario
) {
}
