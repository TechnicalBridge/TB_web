package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code deuda.retirada}: el acreedor la saco de la gestion, con su motivo. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeudaRetiradaDatos(String deudaIdExterno, String motivo) {
}
