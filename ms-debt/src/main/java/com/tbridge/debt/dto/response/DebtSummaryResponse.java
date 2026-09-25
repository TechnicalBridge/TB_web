package com.tbridge.debt.dto.response;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Installment;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Una deuda en la lista del portal.
 *
 * <p>El saldo se calcula (las cuotas pendientes), nunca se guarda: un total
 * almacenado al lado de sus partes termina discrepando de ellas.
 */
@Relation(collectionRelation = "debts", itemRelation = "debt")
@Schema(description = "Una deuda, con su saldo al dia")
public record DebtSummaryResponse(
        @Schema(example = "3") Long id,
        @Schema(description = "El id que le puso el acreedor. Viaja intacto por toda la cadena", example = "CTR-2024-007")
        String externalId,
        @Schema(example = "Patrimonio Inmuebles") String acreedor,
        @Schema(example = "76418902-7") String acreedorRut,
        @Schema(example = "Comercial Nandu SpA") String deudor,
        @Schema(example = "76991245-2") String deudorRut,
        @Schema(example = "Arriendo local comercial") String concepto,
        @Schema(example = "UF") Debt.Currency moneda,
        @Schema(example = "115.50") BigDecimal montoOriginal,
        @Schema(description = "La suma de las cuotas pendientes", example = "96.25") BigDecimal saldo,
        @Schema(example = "19.25") BigDecimal pagado,
        @Schema(example = "repacted") Debt.Status estado,
        Instant actualizada,
        @Schema(description = "Cuotas ya pagadas. Con cuotasTotales, el avance del convenio", example = "1")
        int cuotasPagadas,
        @Schema(description = "Cuotas vigentes: las pagadas mas las pendientes. Las anuladas no cuentan",
                example = "6")
        int cuotasTotales,
        @Schema(description = "Si se paga (o se pago) con un convenio de cuotas", example = "true")
        boolean conConvenio
) {

    /** Con las cuotas de la deuda: de ellas salen el saldo y el avance. */
    public static DebtSummaryResponse from(Debt deuda, List<Installment> cuotas) {
        BigDecimal saldo = cuotas.stream()
                .filter(c -> c.getStatus() == Installment.Status.pending)
                .map(Installment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int pagadas = (int) cuotas.stream().filter(c -> c.getStatus() == Installment.Status.paid).count();
        int vigentes = (int) cuotas.stream().filter(c -> c.getStatus() != Installment.Status.void_).count();
        boolean conConvenio = cuotas.stream()
                .anyMatch(c -> c.getStatus() != Installment.Status.void_ && c.enConvenio());
        return new DebtSummaryResponse(
                deuda.getId(),
                deuda.getExternalId(),
                deuda.getCreditor().getTradeName(),
                deuda.getCreditor().getRut(),
                deuda.getDebtor().getFullName(),
                deuda.getDebtor().getRut(),
                deuda.getConcept(),
                deuda.getCurrency(),
                deuda.getOriginalAmount(),
                saldo,
                deuda.getOriginalAmount().subtract(saldo),
                deuda.getStatus(),
                deuda.getUpdatedAt(),
                pagadas,
                vigentes,
                conConvenio);
    }
}
