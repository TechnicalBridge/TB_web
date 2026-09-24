package com.tbridge.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedir el enlace de respaldo al correo. */
@Schema(description = "El correo al que se manda el enlace, y el RUT si quien lo pide es un deudor")
public record EnlaceRequest(
        @Schema(description = "Solo para deudores. El personal de una empresa lo deja vacio",
                example = "16482337-7", nullable = true)
        @Size(max = 20, message = "El RUT es demasiado largo")
        String rut,

        @Schema(example = "felipe.rojas@correo.cl")
        @NotBlank(message = "Correo no valido")
        @Email(message = "Correo no valido")
        @Size(max = 254, message = "Correo no valido")
        String correo
) {
}
