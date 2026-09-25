package com.tbridge.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que pide el motor de campana (hoy, ms-debt a pedido del personal) para
 * hacerle llegar un codigo a un deudor.
 */
@Schema(description = "Para quien es el codigo y por donde se manda")
public record EmitirCodigoRequest(
        @Schema(example = "16482337-7")
        @NotBlank(message = "Falta el RUT")
        String rut,

        @Schema(description = "Por donde se manda. Sin indicar, por correo", example = "[\"correo\"]")
        List<String> canales,

        @Schema(description = "A que deuda se refiere, para el registro", example = "CTR-2025-014")
        String paraQue,

        @Schema(description = "A donde se manda si uno de los canales es el correo", example = "felipe.rojas@correo.cl")
        String correo,

        @Schema(description = "Quien le escribe al deudor: va en el texto del correo", example = "Patrimonio Inmuebles")
        String acreedor,

        @Schema(description = "Si viene, el correo recuerda la cuota que vence ese dia, en vez de ser el primer aviso",
                example = "2026-10-20", nullable = true)
        LocalDate vence
) {

    /** Sin canales indicados, el codigo va por correo. */
    public List<String> canalesOPorOmision() {
        return canales == null || canales.isEmpty() ? List.of("correo") : canales;
    }
}
