package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una clave de API recien emitida. Se muestra una sola vez: en la base queda su huella. */
@Schema(description = "La clave de API, que no se puede volver a mostrar")
public record ClaveEmitidaResponse(
        @Schema(example = "tbk_2x9...") String clave,
        @Schema(description = "Para reconocerla sin revelarla", example = "tbk_2x9Qa7Lm") String prefijo,
        @Schema(example = "APOFYX") String organizacion,
        @Schema(example = "Guardala ahora: no se puede volver a mostrar") String aviso
) {
}
