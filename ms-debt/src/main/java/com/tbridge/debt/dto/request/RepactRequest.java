package com.tbridge.debt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Aceptar un plan de cuotas. */
@Schema(description = "En cuantas cuotas se quiere pagar el saldo")
public record RepactRequest(
        @Schema(description = "Entre 3 y 24. Sin indicar, 12", example = "6", nullable = true)
        @Min(value = 3, message = "Las cuotas deben estar entre 3 y 24 meses")
        @Max(value = 24, message = "Las cuotas deben estar entre 3 y 24 meses")
        Integer months
) {

    public int mesesOPorOmision() {
        return months == null ? 12 : months;
    }
}
