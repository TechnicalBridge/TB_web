package com.tbridge.payments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** El valor de la UF de un dia, cargado a mano cuando el Banco Central no esta disponible. */
@Schema(description = "Un dia y el valor de la UF ese dia")
public record UfManualRequest(
        @Schema(example = "2026-09-22")
        @NotNull(message = "Se espera { \"dia\": \"2026-09-22\", \"valor\": \"39876.54\" }")
        LocalDate dia,

        @Schema(example = "39876.54")
        @NotNull(message = "Se espera { \"dia\": \"2026-09-22\", \"valor\": \"39876.54\" }")
        @Positive(message = "La UF tiene que ser positiva")
        BigDecimal valor
) {
}
