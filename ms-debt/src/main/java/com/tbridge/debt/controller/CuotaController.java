package com.tbridge.debt.controller;

import com.tbridge.common.exception.ApiError;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.config.OpenApiConfig;
import com.tbridge.debt.dto.response.ConvenioEnRiesgoResponse;
import com.tbridge.debt.dto.response.CuotaPorVencerResponse;
import com.tbridge.debt.service.CuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

/** Las cuotas mirando al calendario: lo que viene, y lo que se atraso. */
@RestController
@RequestMapping("/api/debts")
@Tag(name = "Cuotas", description = "Proximos vencimientos del deudor y convenios en riesgo de la empresa")
@SecurityRequirement(name = OpenApiConfig.JWT)
public class CuotaController {

    private final CuotaService cuotas;

    public CuotaController(CuotaService cuotas) {
        this.cuotas = cuotas;
    }

    @GetMapping("/vencimientos")
    @Operation(summary = "Las cuotas por pagar del deudor, por fecha",
            description = """
                    Solo el deudor. De la que vence primero a la ultima, con los dias que faltan (negativos si ya \
                    vencio). Una deuda sin convenio aparece como una sola cuota por el total. Van en `_embedded.cuotas`.""")
    @ApiResponse(responseCode = "200", description = "Las cuotas")
    @ApiResponse(responseCode = "403", description = "Quien pregunta es una empresa",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CollectionModel<EntityModel<CuotaPorVencerResponse>> vencimientos(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal user) {
        return CollectionModel.of(cuotas.vencimientos(user).stream()
                        .map(c -> EntityModel.of(c, linkTo(DebtController.class).slash(c.deudaId()).withRel("deuda")))
                        .toList(),
                linkTo(CuotaController.class).slash("vencimientos").withSelfRel());
    }

    @GetMapping("/en-riesgo")
    @Operation(summary = "Los convenios con cuotas vencidas",
            description = """
                    Solo la empresa, sobre su cartera. Del mas atrasado al menos: es a quien hay que llamar antes \
                    de que el convenio se caiga. Van en `_embedded.convenios`.""")
    @ApiResponse(responseCode = "200", description = "Los convenios en riesgo")
    @ApiResponse(responseCode = "403", description = "Quien pregunta es un deudor",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CollectionModel<EntityModel<ConvenioEnRiesgoResponse>> enRiesgo(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal user) {
        return CollectionModel.of(cuotas.enRiesgo(user).stream()
                        .map(c -> EntityModel.of(c,
                                linkTo(DebtController.class).slash(c.deudaId()).withRel("deuda"),
                                linkTo(DebtController.class).slash(c.deudaId()).slash("codigo").withRel("enviar-codigo")))
                        .toList(),
                linkTo(CuotaController.class).slash("en-riesgo").withSelfRel());
    }
}
