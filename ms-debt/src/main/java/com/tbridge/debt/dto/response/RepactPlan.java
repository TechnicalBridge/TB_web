package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un plan de cuotas sin interes: el total es lo que se debe hoy, y la ultima
 * cuota absorbe el redondeo.
 */
@Schema(description = "El plan de cuotas para el saldo de una deuda")
public record RepactPlan(
        @Schema(example = "6") int months,
        @Schema(description = "Cada cuota salvo la ultima", example = "19.25") BigDecimal monthlyAmount,
        @Schema(description = "La ultima, con el resto del redondeo", example = "19.25") BigDecimal lastAmount,
        @Schema(description = "Lo mismo que el saldo: sin intereses", example = "115.50") BigDecimal total,
        List<InstallmentPreview> cuotas
) {
}
