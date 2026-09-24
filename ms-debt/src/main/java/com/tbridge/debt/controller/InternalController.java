package com.tbridge.debt.controller;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.common.exception.ApiError;
import com.tbridge.common.exception.ApiException;
import com.tbridge.debt.config.OpenApiConfig;
import com.tbridge.debt.dto.request.EmitirClaveRequest;
import com.tbridge.debt.dto.response.AvanceResponse;
import com.tbridge.debt.dto.response.ClaveEmitidaResponse;
import com.tbridge.debt.dto.response.DebtSnapshotResponse;
import com.tbridge.debt.dto.response.OkResponse;
import com.tbridge.debt.service.ApiKeyService;
import com.tbridge.debt.service.CampanaAvanceService;
import com.tbridge.debt.service.DebtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que un servicio hermano le pregunta o le avisa a ms-debt. No es publico:
 * va detras de la clave interna y el gateway no lo expone.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Interno", description = "Para los otros servicios y operaciones, con la clave interna")
@SecurityRequirement(name = OpenApiConfig.CLAVE_INTERNA)
@ApiResponse(responseCode = "401", description = "Clave interna invalida",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class InternalController {

    private final DebtService debts;
    private final CampanaAvanceService avances;
    private final ApiKeyService claves;
    private final String internalKey;

    public InternalController(DebtService debts, CampanaAvanceService avances, ApiKeyService claves,
                              @Value("${app.internal-key}") String internalKey) {
        this.debts = debts;
        this.avances = avances;
        this.claves = claves;
        this.internalKey = internalKey;
    }

    @GetMapping("/debts/{id}")
    @Operation(summary = "Cuanto se debe y a quien (para ms-payments)",
            description = "ms-payments cobra este monto y no el que manda el navegador.")
    @ApiResponse(responseCode = "200", description = "El monto a cobrar")
    @ApiResponse(responseCode = "404", description = "La deuda o la cuota no existen",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Sin saldo, o la cuota no esta pendiente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public DebtSnapshotResponse deuda(
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @PathVariable Long id,
            @Parameter(description = "Una cuota en particular") @RequestParam(required = false) Long installmentId
    ) {
        exigirClave(clave);
        return debts.snapshotInterno(id, installmentId);
    }

    @PostMapping("/claves")
    @Operation(summary = "Emitir una clave de API",
            description = "Para una organizacion registrada. Se devuelve una sola vez: en la base queda solo su huella.")
    @ApiResponse(responseCode = "200", description = "La clave")
    @ApiResponse(responseCode = "404", description = "No hay organizacion con ese RUT",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ClaveEmitidaResponse clave(
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @Valid @RequestBody EmitirClaveRequest pedido
    ) {
        exigirClave(clave);
        return claves.emitirPara(pedido.rut(), pedido.nombreOPorOmision());
    }

    @PostMapping("/campanas/avance")
    @Operation(summary = "Publicar ahora el avance de las campanas",
            description = "Sin esperar la corrida de las 8:00. Lo usan operaciones y las pruebas.")
    @ApiResponse(responseCode = "200", description = "El avance de cada campana en curso")
    public AvanceResponse avance(
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Key", required = false) String clave) {
        exigirClave(clave);
        return avances.publicarTodas();
    }

    @PostMapping("/events/pago-confirmado")
    @Operation(summary = "El aviso de un pago concretado (desde ms-payments)",
            description = "El camino por HTTP; con RabbitMQ el mismo aviso llega por la cola. Un aviso repetido no abona dos veces.")
    @ApiResponse(responseCode = "200", description = "Recibido")
    public OkResponse pagoConfirmado(
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @RequestBody PagoConfirmado aviso
    ) {
        exigirClave(clave);
        debts.onPagoConfirmado(aviso);
        return OkResponse.OK;
    }

    private void exigirClave(String clave) {
        if (clave == null || !clave.equals(internalKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave interna invalida");
        }
    }
}
