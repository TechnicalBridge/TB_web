package com.tbridge.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrar con RUT y codigo.
 *
 * <p>El formato del RUT y su digito verificador los revisa el servicio: aca
 * solo se exige que venga algo y que no sea absurdamente largo.
 */
@Schema(description = "RUT del deudor y el codigo de acceso que le llego")
public record AccesoRequest(
        @Schema(description = "Con o sin puntos y guion", example = "16.482.337-7")
        @NotBlank(message = "Falta el RUT")
        @Size(max = 20, message = "El RUT es demasiado largo")
        String rut,

        @Schema(description = "Seis caracteres, sin 0, O, 1, I ni L", example = "K7M2QX")
        @NotBlank(message = "Falta el codigo")
        @Size(max = 16, message = "El codigo es demasiado largo")
        String codigo
) {
}
