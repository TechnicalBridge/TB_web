package com.digitalbot.dto;

import com.digitalbot.catalog.PlanCatalog;

import java.util.List;

public record QuoteResult(
        PlanCatalog.Plan plan,
        String billing,
        int users,
        List<ExtraLine> extras,
        int amount,
        String currency,
        String periodLabel
) {}
