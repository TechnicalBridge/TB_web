package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un plan simulado: no compromete nada hasta que el deudor lo acepta. */
@Schema(description = "El plan que resultaria con ese plazo")
public record SimulacionResponse(RepactPlan plan) {
}
