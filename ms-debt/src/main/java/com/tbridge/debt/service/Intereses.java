package com.tbridge.debt.service;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtCharge;
import com.tbridge.debt.model.Installment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El interes de mora de una deuda: cuanto crecio cada cuota pendiente por el
 * atraso.
 *
 * <p>No guarda nada ni lee la base: lo usan la vista de la deuda, el cobro y
 * el convenio, y asi los tres dicen lo mismo.
 *
 * <p><b>Como se cuenta.</b> La tasa es mensual y la pacta el acreedor. La mora
 * es interes simple sobre el capital vencido, por dia (la tasa entre 30),
 * desde el dia siguiente al vencimiento. Simple porque la Ley 18.010 no deja
 * capitalizar la mora, y solo sobre capital porque es lo unico sobre lo que
 * corre.
 *
 * <ul>
 *   <li><b>Una cuota de convenio</b> crece sobre su capital (la cuota menos el
 *       interes del convenio que ya trae), desde su vencimiento.</li>
 *   <li><b>Una cuota sin convenio</b> cubre los cargos del acreedor, cada uno con
 *       su vencimiento: un arriendo de agosto atrasado crece desde agosto aunque
 *       la cuota venza con el ultimo mes. Las cuotas sueltas cubren los cargos
 *       mas nuevos: los mas antiguos son los que el convenio o un pago ya
 *       cubrieron.</li>
 * </ul>
 */
public final class Intereses {

    private static final MathContext PRECISION = MathContext.DECIMAL64;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal TREINTA = BigDecimal.valueOf(30);

    private Intereses() {
    }

    /** Un pedazo de capital y desde cuando esta vencido. */
    private record Tramo(BigDecimal monto, LocalDate vence) {}

    /**
     * La mora de cada cuota pendiente al dia {@code hoy}, por id de cuota. Sin
     * tasa, vacio: la deuda no genera intereses.
     */
    public static Map<Long, BigDecimal> deMora(Debt deuda, List<DebtCharge> cargos, List<Installment> cuotas,
                                               LocalDate hoy) {
        Map<Long, BigDecimal> mora = new HashMap<>();
        BigDecimal tasa = deuda.getInterestRate();
        if (tasa == null || tasa.signum() <= 0) {
            return mora;
        }
        BigDecimal porDia = tasa.divide(CIEN, PRECISION).divide(TREINTA, PRECISION);
        List<Installment> pendientes = cuotas.stream()
                .filter(c -> c.getStatus() == Installment.Status.pending)
                .sorted(DebtService.EN_ORDEN)
                .toList();

        for (Installment cuota : pendientes) {
            if (cuota.enConvenio()) {
                mora.put(cuota.getId(), redondear(interes(cuota.capital(), cuota.getDueDate(), hoy, porDia),
                        deuda.getCurrency()));
            }
        }

        List<Installment> sueltas = pendientes.stream().filter(c -> !c.enConvenio()).toList();
        Deque<Tramo> tramos = tramosDe(cargos, sueltas);
        for (Installment cuota : sueltas) {
            BigDecimal falta = cuota.getAmount();
            BigDecimal suma = BigDecimal.ZERO;
            while (falta.signum() > 0 && !tramos.isEmpty()) {
                Tramo tramo = tramos.pollFirst();
                BigDecimal toma = tramo.monto().min(falta);
                suma = suma.add(interes(toma, tramo.vence(), hoy, porDia));
                falta = falta.subtract(toma);
                if (toma.compareTo(tramo.monto()) < 0) {
                    tramos.addFirst(new Tramo(tramo.monto().subtract(toma), tramo.vence()));
                }
            }
            //  Lo que ningun cargo explica corre desde el vencimiento de la cuota.
            suma = suma.add(interes(falta, cuota.getDueDate(), hoy, porDia));
            mora.put(cuota.getId(), redondear(suma, deuda.getCurrency()));
        }
        return mora;
    }

    /**
     * Los dias de mora de la deuda al dia {@code hoy}: los del cargo impago mas
     * antiguo, que es lo que mide el tramo del descuento. Sin cargos que lo
     * expliquen, los de la cuota suelta pendiente mas antigua.
     */
    public static long diasDeMora(List<DebtCharge> cargos, List<Installment> cuotas, LocalDate hoy) {
        List<Installment> sueltas = cuotas.stream()
                .filter(c -> c.getStatus() == Installment.Status.pending && !c.enConvenio())
                .sorted(DebtService.EN_ORDEN)
                .toList();
        Tramo masAntiguo = tramosDe(cargos, sueltas).peekFirst();
        LocalDate vence = masAntiguo != null ? masAntiguo.vence()
                : sueltas.stream().map(Installment::getDueDate).min(Comparator.naturalOrder()).orElse(null);
        return vence == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(vence, hoy));
    }

    /** La suma de la mora de todas las cuotas. */
    public static BigDecimal total(Map<Long, BigDecimal> mora) {
        return mora.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** A la unidad de la moneda: pesos enteros, y en UF centesimas. */
    public static BigDecimal redondear(BigDecimal monto, Debt.Currency moneda) {
        return monto.setScale(moneda == Debt.Currency.UF ? 2 : 0, RoundingMode.HALF_UP);
    }

    /**
     * Los cargos que cubren las cuotas sueltas, del mas antiguo al mas nuevo.
     * Se toman desde el mas nuevo hacia atras hasta juntar lo que suman las
     * cuotas: lo que falta, lo cubrio el convenio o ya se pago.
     */
    private static Deque<Tramo> tramosDe(List<DebtCharge> cargos, List<Installment> sueltas) {
        BigDecimal porCubrir = sueltas.stream().map(Installment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Deque<Tramo> tramos = new ArrayDeque<>();
        List<DebtCharge> delMasNuevo = cargos.stream()
                .sorted(Comparator.comparing(DebtCharge::getDueDate).reversed())
                .toList();
        for (DebtCharge cargo : delMasNuevo) {
            if (porCubrir.signum() <= 0) {
                break;
            }
            BigDecimal toma = cargo.getAmount().min(porCubrir);
            tramos.addFirst(new Tramo(toma, cargo.getDueDate()));
            porCubrir = porCubrir.subtract(toma);
        }
        return tramos;
    }

    private static BigDecimal interes(BigDecimal capital, LocalDate vence, LocalDate hoy, BigDecimal porDia) {
        long dias = ChronoUnit.DAYS.between(vence, hoy);
        if (dias <= 0 || capital.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return capital.multiply(porDia, PRECISION).multiply(BigDecimal.valueOf(dias), PRECISION);
    }
}
