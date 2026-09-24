package com.tbridge.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una sesion recien abierta o renovada.
 *
 * <p>La llave de renovacion no viene aca: va en la cookie {@code tb_renovacion},
 * que ningun script de la pagina puede leer.
 */
@Schema(description = "El JWT de la sesion y quien la tiene")
public record SesionResponse(
        @Schema(description = "JWT para la cabecera Authorization: Bearer", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        UsuarioResponse user,

        @Schema(description = "Cuanto vive el JWT. Antes de eso hay que renovar", example = "900")
        long expiraEnSegundos
) {
}
