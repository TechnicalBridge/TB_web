package com.tbridge.debt.service;

import com.tbridge.common.web.ApiException;
import com.tbridge.debt.dto.RepactPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepactationServiceTest {

    private final RepactationService service = new RepactationService();

    @Test
    void splitsRemainderIntoLastInstallment() {
        RepactPlan plan = service.simulate(new BigDecimal("100000"), 3, LocalDate.of(2026, 1, 15));
        assertEquals(3, plan.cuotas().size());
        BigDecimal sum = plan.cuotas().stream()
                .map(c -> c.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100000"), sum);
        assertEquals(LocalDate.of(2026, 1, 15), plan.cuotas().getFirst().dueDate());
        assertEquals(LocalDate.of(2026, 3, 15), plan.cuotas().getLast().dueDate());
    }

    @Test
    void rejectsOutOfRangeMonths() {
        assertThrows(ApiException.class, () -> service.simulate(new BigDecimal("100000"), 2, LocalDate.now()));
        assertThrows(ApiException.class, () -> service.simulate(new BigDecimal("100000"), 36, LocalDate.now()));
    }
}
