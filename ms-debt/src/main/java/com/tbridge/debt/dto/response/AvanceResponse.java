package com.tbridge.debt.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.tbridge.debt.dto.evento.CampanaAvanceDatos;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** El avance publicado de cada campana en curso. */
@Schema(description = "El avance de cada campana y a cuantos suscriptores se aviso")
public record AvanceResponse(List<Campana> campanas) {

    @Schema(description = "El avance de una campana")
    public record Campana(
            @JsonUnwrapped CampanaAvanceDatos datos,
            @Schema(description = "Cuantos suscriptores quedaron por avisar", example = "1") int avisados
    ) {
    }
}
