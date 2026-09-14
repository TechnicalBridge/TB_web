package com.tbridge.debt.service;

import com.tbridge.common.web.ApiException;
import com.tbridge.debt.dto.InstallmentPreview;
import com.tbridge.debt.dto.RepactPlan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepactationService {

    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 24;

    public RepactPlan simulate(BigDecimal remaining, int months, LocalDate start) {
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No hay saldo para repactar");
        }
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Las cuotas deben estar entre 3 y 24 meses");
        }
        LocalDate from = start == null ? LocalDate.now().plusMonths(1) : start;
        BigDecimal total = remaining.setScale(0, RoundingMode.HALF_UP);
        BigDecimal monthly = total.divide(BigDecimal.valueOf(months), 0, RoundingMode.DOWN);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El monto es demasiado bajo para ese plazo");
        }
        BigDecimal assigned = monthly.multiply(BigDecimal.valueOf(months - 1L));
        BigDecimal last = total.subtract(assigned);
        List<InstallmentPreview> cuotas = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            BigDecimal amount = i == months ? last : monthly;
            cuotas.add(new InstallmentPreview(i, from.plusMonths(i - 1L), amount));
        }
        return new RepactPlan(months, monthly, last, total, cuotas);
    }
}
