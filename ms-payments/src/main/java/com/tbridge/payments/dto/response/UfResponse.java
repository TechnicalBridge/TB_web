package com.tbridge.payments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** El valor de la UF que quedo guardado para un dia. */
@Schema(description = "El valor de la UF de un dia y de donde salio")
public record UfResponse(
        @Schema(example = "2026-09-22") LocalDate dia,
        @Schema(example = "39876.54") BigDecimal valor,
        @Schema(example = "manual") String fuente
) {
}
