package com.tbridge.debt.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.dto.response.RepactPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepactationServiceTest {

    private final RepactationService service = new RepactationService();

    @Test
    void splitsRemainderIntoLastInstallment() {
        RepactPlan plan = service.simulate(new BigDecimal("100000"), Debt.Currency.CLP, 3, LocalDate.of(2026, 1, 15));
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
        assertThrows(ApiException.class, () -> service.simulate(new BigDecimal("100000"), Debt.Currency.CLP, 2, LocalDate.now()));
        assertThrows(ApiException.class, () -> service.simulate(new BigDecimal("100000"), Debt.Currency.CLP, 36, LocalDate.now()));
    }

    /**
     * Una deuda en UF se reparte en centesimas y el total no cambia. Antes se
     * redondeaba a UF enteras: 115,50 pasaba a 116.
     */
    @Test
    void enUfNoCambiaElMontoDeLaDeuda() {
        RepactPlan plan = service.simulate(new BigDecimal("115.50"), Debt.Currency.UF, 6, LocalDate.of(2026, 10, 20));
        assertEquals(new BigDecimal("115.50"), plan.total());
        assertEquals(new BigDecimal("19.25"), plan.monthlyAmount());
        BigDecimal suma = plan.cuotas().stream().map(c -> c.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("115.50"), suma);
    }

    @Test
    void enUfElRestoVaALaUltimaCuota() {
        RepactPlan plan = service.simulate(new BigDecimal("100.00"), Debt.Currency.UF, 3, LocalDate.of(2026, 10, 20));
        assertEquals(new BigDecimal("33.33"), plan.monthlyAmount());
        assertEquals(new BigDecimal("33.34"), plan.lastAmount());
    }
}
