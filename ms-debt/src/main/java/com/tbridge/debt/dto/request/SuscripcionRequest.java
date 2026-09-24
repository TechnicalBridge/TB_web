package com.tbridge.debt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Contrato 3: a donde avisarle los eventos a quien llama. */
@Schema(description = "La URL que recibe los eventos y cuales")
public record SuscripcionRequest(
        @Schema(example = "https://apofyx.example/api/v1/eventos") String url,
        @Schema(description = "Sin indicar, todos", nullable = true,
                example = "[\"pago.confirmado\",\"deuda.saldada\",\"repactacion.aceptada\",\"deuda.retirada\"]")
        List<String> eventos
) {
}
