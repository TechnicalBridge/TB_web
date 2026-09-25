package com.tbridge.debt.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Lo unico que el deudor puede cambiar de sus datos: si quiere los
 * recordatorios. El correo y el telefono son del acreedor; si estan mal, se
 * corrigen alla y llegan en la cartera siguiente.
 */
@Schema(description = "Encender o apagar el correo que recuerda una cuota")
public record MisDatosRequest(
        @Schema(example = "false")
        @NotNull(message = "Indica si quieres los recordatorios")
        Boolean recordatorios
) {
}
