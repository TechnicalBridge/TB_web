package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * {@code campana.avance}: el acumulado de una campana hasta hoy.
 *
 * <p>Va lo que DataBridge mide de verdad. Lo que no mide —si el mensaje se
 * entrego, si respondieron, las bajas— no viaja en cero: no viaja, para que
 * nadie lea un cero como "ninguno" cuando significa "no lo se".
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "El embudo de una campana hasta hoy")
public record CampanaAvanceDatos(
        @Schema(example = "APX-CMP-8") String campanaIdExterno,
        @Schema(example = "2026-09-24") String fechaCorte,
        @Schema(example = "3") int deudas,
        @Schema(description = "Codigos de acceso enviados", example = "3") int enviados,
        @Schema(example = "2") int ingresosPortal,
        @Schema(example = "1") int repactaciones,
        @Schema(example = "1") int pagos,
        @Schema(example = "1") int saldadas,
        @Schema(example = "0") int disputas,
        @Schema(example = "0") int retiradas,
        @Schema(example = "410000") long recuperadoClp,
        @Schema(example = "19.25") BigDecimal recuperadoUf
) {
}
