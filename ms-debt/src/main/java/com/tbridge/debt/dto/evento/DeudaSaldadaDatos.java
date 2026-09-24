package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code deuda.saldada}: la deuda quedo en cero. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeudaSaldadaDatos(String deudaIdExterno, String saldadaEn) {
}
