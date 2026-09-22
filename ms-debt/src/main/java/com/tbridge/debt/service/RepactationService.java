package com.tbridge.debt.service;

import com.tbridge.common.web.ApiException;
import com.tbridge.debt.domain.Debt;
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

    /**
     * El plan de cuotas para un saldo.
     *
     * <p>Se redondea a la unidad de la moneda: pesos enteros en CLP y
     * centesimas en UF. Redondear una deuda en UF a enteros le cambiaba el
     * monto: UF 115,50 pasaba a UF 116, media UF que el deudor no debia.
     */
    public RepactPlan simulate(BigDecimal remaining, Debt.Currency moneda, int months, LocalDate start) {
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No hay saldo para repactar");
        }
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Las cuotas deben estar entre 3 y 24 meses");
        }
        LocalDate from = start == null ? LocalDate.now().plusMonths(1) : start;
        int decimales = moneda == Debt.Currency.UF ? 2 : 0;
        BigDecimal total = remaining.setScale(decimales, RoundingMode.HALF_UP);
        BigDecimal monthly = total.divide(BigDecimal.valueOf(months), decimales, RoundingMode.DOWN);
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
