package com.tbridge.debt.service;

import com.tbridge.debt.exception.ApiException;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.dto.response.InstallmentPreview;
import com.tbridge.debt.dto.response.RepactPlan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepactationService {

    public static final int MIN_MONTHS = 3;
    public static final int MAX_MONTHS = 24;

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    /** El plan de cuotas para un saldo, sin intereses. */
    public RepactPlan simulate(BigDecimal remaining, Debt.Currency moneda, int months, LocalDate start) {
        return simulate(remaining, BigDecimal.ZERO, null, moneda, months, start);
    }

    /**
     * El plan de cuotas para una deuda.
     *
     * <p>Lo que se repacta es el capital pendiente mas la mora acumulada hasta
     * hoy. Sin tasa, las cuotas son iguales y la ultima absorbe el redondeo.
     * Con tasa, son de sistema frances: todas iguales, cada una con el interes
     * del mes sobre lo que queda por pagar y el resto a capital, y la ultima
     * cierra lo que quede.
     *
     * <p>Se redondea a la unidad de la moneda: pesos enteros en CLP y
     * centesimas en UF. Redondear una deuda en UF a enteros le cambiaba el
     * monto: UF 115,50 pasaba a UF 116, media UF que el deudor no debia.
     *
     * @param capital     el capital pendiente
     * @param mora        la mora acumulada hasta hoy
     * @param tasaMensual la tasa del acreedor, en porcentaje mensual; null sin intereses
     */
    public RepactPlan simulate(BigDecimal capital, BigDecimal mora, BigDecimal tasaMensual, Debt.Currency moneda,
                               int months, LocalDate start) {
        BigDecimal sinMora = mora == null ? BigDecimal.ZERO : mora;
        if (capital == null || capital.add(sinMora).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No hay saldo para repactar");
        }
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Las cuotas deben estar entre 3 y 24 meses");
        }
        LocalDate from = start == null ? LocalDate.now().plusMonths(1) : start;
        int decimales = moneda == Debt.Currency.UF ? 2 : 0;
        BigDecimal aRepactar = capital.add(sinMora).setScale(decimales, RoundingMode.HALF_UP);
        boolean conInteres = tasaMensual != null && tasaMensual.signum() > 0;

        List<InstallmentPreview> cuotas = conInteres
                ? frances(aRepactar, tasaMensual, decimales, months, from)
                : iguales(aRepactar, decimales, months, from);

        BigDecimal total = cuotas.stream().map(InstallmentPreview::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interesConvenio = cuotas.stream().map(InstallmentPreview::interest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepactPlan(months, cuotas.getFirst().amount(), cuotas.getLast().amount(), total, cuotas,
                capital.setScale(decimales, RoundingMode.HALF_UP), sinMora.setScale(decimales, RoundingMode.HALF_UP),
                aRepactar, conInteres ? tasaMensual : null, interesConvenio);
    }

    private static List<InstallmentPreview> iguales(BigDecimal total, int decimales, int months, LocalDate from) {
        BigDecimal monthly = total.divide(BigDecimal.valueOf(months), decimales, RoundingMode.DOWN);
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El monto es demasiado bajo para ese plazo");
        }
        BigDecimal last = total.subtract(monthly.multiply(BigDecimal.valueOf(months - 1L)));
        List<InstallmentPreview> cuotas = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            cuotas.add(new InstallmentPreview(i, from.plusMonths(i - 1L), i == months ? last : monthly,
                    BigDecimal.ZERO.setScale(decimales)));
        }
        return cuotas;
    }

    /** Cuota = P·i / (1 − (1 + i)^−n), con i la tasa mensual. */
    private static List<InstallmentPreview> frances(BigDecimal principal, BigDecimal tasaMensual, int decimales,
                                                    int months, LocalDate from) {
        BigDecimal i = tasaMensual.divide(BigDecimal.valueOf(100), PRECISION);
        BigDecimal factor = BigDecimal.ONE.add(i).pow(months, PRECISION);
        BigDecimal cuota = principal.multiply(i, PRECISION).multiply(factor, PRECISION)
                .divide(factor.subtract(BigDecimal.ONE), PRECISION)
                .setScale(decimales, RoundingMode.HALF_UP);

        List<InstallmentPreview> cuotas = new ArrayList<>();
        BigDecimal saldo = principal;
        for (int k = 1; k <= months; k++) {
            BigDecimal interes = saldo.multiply(i, PRECISION).setScale(decimales, RoundingMode.HALF_UP);
            //  La ultima paga lo que quede de capital: ahi se absorbe el redondeo.
            BigDecimal aCapital = k == months ? saldo : cuota.subtract(interes);
            if (aCapital.signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El monto es demasiado bajo para ese plazo");
            }
            cuotas.add(new InstallmentPreview(k, from.plusMonths(k - 1L), aCapital.add(interes), interes));
            saldo = saldo.subtract(aCapital);
        }
        return cuotas;
    }
}
