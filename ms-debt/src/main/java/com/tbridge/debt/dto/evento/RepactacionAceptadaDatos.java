package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code repactacion.aceptada}: el deudor acepto un plan de cuotas. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RepactacionAceptadaDatos(
        String deudaIdExterno,
        int cuotas,
        Number montoCuota,
        String moneda,
        String primeraCuota
) {
}
