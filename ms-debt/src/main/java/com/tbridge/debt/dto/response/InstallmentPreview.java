package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cuota de un plan simulado. */
@Schema(description = "Una cuota del plan")
public record InstallmentPreview(
        @Schema(example = "1") int number,
        @Schema(example = "2026-10-24") LocalDate dueDate,
        @Schema(example = "19.25") BigDecimal amount
) {
}
