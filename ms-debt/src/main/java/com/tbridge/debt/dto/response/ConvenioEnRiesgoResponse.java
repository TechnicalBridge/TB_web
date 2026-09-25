package com.tbridge.debt.dto.response;

import com.tbridge.debt.model.Debt;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un convenio con cuotas vencidas sin pagar. Es a quien hay que llamar antes
 * de que el convenio se caiga.
 */
@Relation(collectionRelation = "convenios", itemRelation = "convenio")
@Schema(description = "Un convenio con cuotas vencidas")
public record ConvenioEnRiesgoResponse(
        @Schema(example = "7") Long deudaId,
        @Schema(example = "CTR-2025-027") String externalId,
        @Schema(example = "Patrimonio Inmuebles") String acreedor,
        @Schema(example = "Ignacio Tapia Rojas") String deudor,
        @Schema(example = "17893456-2") String deudorRut,
        @Schema(example = "CLP") Debt.Currency moneda,
        @Schema(example = "1") int cuotasVencidas,
        @Schema(example = "116667") BigDecimal montoVencido,
        @Schema(description = "El vencimiento de la cuota impaga mas antigua", example = "2026-09-20")
        LocalDate vencidaDesde,
        @Schema(example = "5") long diasAtraso,
        @Schema(example = "0") int cuotasPagadas,
        @Schema(example = "6") int cuotasTotales,
        @Schema(example = "700000") BigDecimal saldo,
        @Schema(description = "El ultimo pago que hizo, si hizo alguno", nullable = true) Instant ultimoPago
) {
}
