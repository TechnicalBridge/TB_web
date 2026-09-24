package com.tbridge.debt.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tbridge.debt.model.Mandate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Un mandato registrado: la agencia cobra por cuenta de ese acreedor. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "El mandato, nuevo o el que ya existia")
public record MandatoResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "76418902-7") String acreedorRut,
        @Schema(example = "2026-09-01") LocalDate vigenteDesde,
        @Schema(example = "120") Short moraMaximaDias
) {

    public static MandatoResponse from(Mandate mandato) {
        return new MandatoResponse(mandato.getId(), mandato.getCreditor().getRut(), mandato.getValidFrom(),
                mandato.getMaxOverdueDays());
    }
}
