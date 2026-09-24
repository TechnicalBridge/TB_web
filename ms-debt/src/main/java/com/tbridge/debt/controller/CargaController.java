package com.tbridge.debt.controller;

import com.tbridge.common.exception.ApiError;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.config.OpenApiConfig;
import com.tbridge.debt.dto.response.CarteraResponse;
import com.tbridge.debt.dto.response.OpcionesCargaResponse;
import com.tbridge.debt.exception.ErrorContrato;
import com.tbridge.debt.service.CargaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * La carga de cartera por archivo desde el portal de empresas (arrastrar y
 * soltar). Quien carga es la organizacion de la sesion, nunca un RUT que
 * venga en el formulario.
 */
@RestController
@RequestMapping("/api/debts/cartera")
@Tag(name = "Carga de cartera", description = "El CSV del contrato, desde el portal de empresas")
@SecurityRequirement(name = OpenApiConfig.JWT)
public class CargaController {

    private final CargaService cargas;

    public CargaController(CargaService cargas) {
        this.cargas = cargas;
    }

    @GetMapping("/opciones")
    @Operation(summary = "Por cuenta de quien puedo cargar",
            description = "La propia empresa si es acreedora, y cada acreedor que le dio un mandato vigente, con sus campanas.")
    @ApiResponse(responseCode = "200", description = "Los acreedores y campanas")
    @ApiResponse(responseCode = "403", description = "Quien pregunta no es una empresa",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OpcionesCargaResponse opciones(@Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal user) {
        return cargas.opciones(user);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cargar una cartera en CSV",
            description = """
                    El CSV del contrato (seccion 6.6): separador `;`, una fila por cargo. Entra por la misma \
                    ingesta que la API, con aceptacion parcial e idempotencia: cargar el mismo archivo dos \
                    veces no duplica nada.""")
    @ApiResponse(responseCode = "200", description = "Cuantas deudas entraron y cuales no, con su motivo")
    @ApiResponse(responseCode = "400", description = "Al archivo le faltan columnas o sus filas no calzan",
            content = @Content(schema = @Schema(implementation = ErrorContrato.class)))
    @ApiResponse(responseCode = "409", description = "Ese id de lote ya se uso con otro contenido",
            content = @Content(schema = @Schema(implementation = ErrorContrato.class)))
    public CarteraResponse subir(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal user,
            @Parameter(description = "El CSV") @RequestPart("archivo") MultipartFile archivo,
            @Parameter(example = "CSV-2026-09-24-01") @RequestParam("lote_id_externo") String loteId,
            @Parameter(example = "2026-09-24") @RequestParam("fecha_corte") String fechaCorte,
            @Parameter(example = "76418902-7") @RequestParam("acreedor_rut") String acreedorRut,
            @Parameter(example = "APX-CMP-8") @RequestParam(value = "campana_id_externo", required = false) String campana
    ) throws IOException {
        return cargas.subir(user, archivo.getBytes(), loteId, fechaCorte, acreedorRut, campana);
    }
}
