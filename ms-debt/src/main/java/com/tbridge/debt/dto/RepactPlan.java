package com.tbridge.debt.dto;

import java.math.BigDecimal;
import java.util.List;

public record RepactPlan(
        int months,
        BigDecimal monthlyAmount,
        BigDecimal lastAmount,
        BigDecimal total,
        List<InstallmentPreview> cuotas
) {
}
