package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** El correo del deudor, tal como lo registro el acreedor en su cartera. */
@Schema(description = "El correo que registro el acreedor para ese RUT")
public record CorreoDelDeudorResponse(@Schema(example = "felipe.rojas@correo.cl") String correo) {
}
