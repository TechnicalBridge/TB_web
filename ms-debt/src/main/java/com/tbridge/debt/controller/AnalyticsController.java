package com.tbridge.debt.controller;

import com.tbridge.common.exception.ApiError;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.config.OpenApiConfig;
import com.tbridge.debt.dto.response.ResumenResponse;
import com.tbridge.debt.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@Tag(name = "Analitica", description = "Los numeros de la cartera de la empresa")
@SecurityRequirement(name = OpenApiConfig.JWT)
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/analytics/summary")
    @Operation(summary = "El resumen de mi cartera",
            description = "Solo la empresa, y solo de su cartera: saldo y recuperado por moneda y por acreedor, y lo recuperado cada dia del ultimo mes.")
    @ApiResponse(responseCode = "200", description = "El resumen")
    @ApiResponse(responseCode = "403", description = "Quien pregunta no es una empresa registrada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public EntityModel<ResumenResponse> summary(@Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal user) {
        return EntityModel.of(analytics.resumenPara(user),
                linkTo(methodOn(AnalyticsController.class).summary(null)).withSelfRel(),
                linkTo(methodOn(DebtController.class).list(null)).withRel("deudas"));
    }
}
