package com.tbridge.debt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Pausar, reanudar o terminar una campana desde el portal. */
@Schema(description = "El estado nuevo de la campana")
public record EstadoCampanaRequest(
        @NotBlank(message = "Falta el estado")
        @Schema(allowableValues = {"en_curso", "pausada", "terminada"}, example = "pausada") String estado
) {
}
