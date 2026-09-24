package com.tbridge.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Quien es el dueno del JWT con que se pregunta. */
@Schema(description = "El usuario de la sesion en curso")
public record MeResponse(UsuarioResponse user) {
}
