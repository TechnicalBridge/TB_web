package com.tbridge.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La respuesta al pedir un enlace.
 *
 * <p>Es la misma exista o no el correo: si fuera distinta, serviria para
 * averiguar quien esta en cartera.
 */
@Schema(description = "Aviso de que, si el correo corresponde, el enlace va en camino")
public record EnlaceResponse(
        @Schema(example = "true") boolean ok,
        @Schema(example = "Si ese correo esta en cartera, recibiras un enlace de acceso.") String mensaje,
        @Schema(example = "15") long expiraEnMinutos
) {
}
