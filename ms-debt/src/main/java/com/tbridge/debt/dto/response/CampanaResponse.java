package com.tbridge.debt.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tbridge.debt.model.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una campana registrada.
 *
 * <p>{@code canales} viaja como el texto JSON que se guardo, tal como lo
 * devolvia la version 1 del contrato: cambiarlo a una lista romperia a quien
 * ya lo lea asi.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "La campana, nueva o actualizada")
public record CampanaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "APX-CMP-8") String idExterno,
        @Schema(example = "76418902-7") String acreedorRut,
        @Schema(description = "Los canales, como texto JSON", example = "[\"whatsapp\",\"correo\"]") String canales
) {

    public static CampanaResponse from(Campaign campana) {
        return new CampanaResponse(campana.getId(), campana.getExternalId(), campana.getCreditor().getRut(),
                campana.getChannels());
    }
}
