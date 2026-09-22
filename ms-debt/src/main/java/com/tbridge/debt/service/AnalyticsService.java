package com.tbridge.debt.service;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtEvent;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.repo.DebtEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los numeros de UNA cartera.
 *
 * <p>Recibe la organizacion, no un booleano: asi no existe la posibilidad de
 * llamar a este servicio "para todos". La version anterior sumaba
 * {@code findAll()} y le mostraba a cada acreedor el total de la plataforma.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final ZoneId CHILE = ZoneId.of("America/Santiago");
    private static final int DIAS = 30;

    private final DebtRepository debts;
    private final DebtEventRepository eventos;
    private final DebtService servicio;

    public AnalyticsService(DebtRepository debts, DebtEventRepository eventos, DebtService servicio) {
        this.debts = debts;
        this.eventos = eventos;
        this.servicio = servicio;
    }

    public Map<String, Object> resumen(Organization organizacion) {
        List<Debt> cartera = debts.carteraDe(organizacion);

        Map<String, BigDecimal[]> porMoneda = new LinkedHashMap<>();
        int activas = 0;
        int pagadas = 0;
        int retiradas = 0;
        int enConvenio = 0;

        for (Debt deuda : cartera) {
            BigDecimal saldo = servicio.saldo(deuda);
            BigDecimal pagado = deuda.getOriginalAmount().subtract(saldo);
            BigDecimal[] acumulado = porMoneda.computeIfAbsent(
                    deuda.getCurrency().name(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acumulado[0] = acumulado[0].add(saldo);
            acumulado[1] = acumulado[1].add(pagado);
            switch (deuda.getStatus()) {
                case paid -> pagadas++;
                case withdrawn -> retiradas++;
                case repacted -> { enConvenio++; activas++; }
                default -> activas++;
            }
        }

        List<Map<String, Object>> monedas = new ArrayList<>();
        porMoneda.forEach((moneda, valores) -> {
            BigDecimal origen = valores[0].add(valores[1]);
            BigDecimal tasa = origen.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : valores[1].multiply(BigDecimal.valueOf(100))
                        .divide(origen, 1, RoundingMode.HALF_UP);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("moneda", moneda);
            fila.put("saldo", valores[0]);
            fila.put("recuperado", valores[1]);
            fila.put("tasaRecuperacion", tasa);
            monedas.add(fila);
        });

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("organizacion", organizacion.getTradeName());
        cuerpo.put("organizacionRut", organizacion.getRut());
        cuerpo.put("deudas", cartera.size());
        cuerpo.put("activas", activas);
        cuerpo.put("enConvenio", enConvenio);
        cuerpo.put("pagadas", pagadas);
        cuerpo.put("retiradas", retiradas);
        cuerpo.put("porMoneda", monedas);
        cuerpo.put("porAcreedor", porAcreedor(cartera));
        cuerpo.put("recuperadoPorDia", recuperadoPorDia(cartera));
        return cuerpo;
    }

    /**
     * Para una agencia que cobra a varios acreedores: cuanto queda y cuanto se
     * recupero de cada uno, por moneda.
     */
    private List<Map<String, Object>> porAcreedor(List<Debt> cartera) {
        Map<String, Map<String, Object>> filas = new LinkedHashMap<>();
        for (Debt deuda : cartera) {
            String clave = deuda.getCreditor().getRut() + "|" + deuda.getCurrency();
            Map<String, Object> fila = filas.computeIfAbsent(clave, k -> {
                Map<String, Object> nueva = new LinkedHashMap<>();
                nueva.put("acreedor", deuda.getCreditor().getTradeName());
                nueva.put("moneda", deuda.getCurrency().name());
                nueva.put("saldo", BigDecimal.ZERO);
                nueva.put("recuperado", BigDecimal.ZERO);
                return nueva;
            });
            BigDecimal saldo = servicio.saldo(deuda);
            fila.put("saldo", ((BigDecimal) fila.get("saldo")).add(saldo));
            fila.put("recuperado", ((BigDecimal) fila.get("recuperado"))
                    .add(deuda.getOriginalAmount().subtract(saldo)));
        }
        return new ArrayList<>(filas.values());
    }

    /**
     * Lo que entro cada dia de los ultimos 30, segun los pagos aplicados. Dias
     * en Chile, y con ceros en los dias sin pagos: un grafico que se salta los
     * dias vacios muestra una tendencia que no existe.
     */
    private Map<String, List<Map<String, Object>>> recuperadoPorDia(List<Debt> cartera) {
        LocalDate hoy = LocalDate.now(CHILE);
        LocalDate desde = hoy.minusDays(DIAS - 1L);
        Map<String, TreeMap<LocalDate, BigDecimal>> porMoneda = new LinkedHashMap<>();
        if (!cartera.isEmpty()) {
            Instant inicio = desde.atStartOfDay(CHILE).toInstant();
            for (DebtEvent pago : eventos.findByDebtInAndTypeAndOccurredAtAfter(
                    cartera, DebtEvent.Type.payment_applied, inicio)) {
                if (pago.getAmount() == null || pago.getCurrency() == null) {
                    continue;
                }
                LocalDate dia = pago.getOccurredAt().atZone(CHILE).toLocalDate();
                porMoneda.computeIfAbsent(pago.getCurrency().name(), k -> new TreeMap<>())
                        .merge(dia, pago.getAmount(), BigDecimal::add);
            }
        }
        Map<String, List<Map<String, Object>>> series = new LinkedHashMap<>();
        porMoneda.forEach((moneda, dias) -> {
            List<Map<String, Object>> puntos = new ArrayList<>();
            for (LocalDate dia = desde; !dia.isAfter(hoy); dia = dia.plus(1, ChronoUnit.DAYS)) {
                puntos.add(Map.of("dia", dia.toString(), "monto", dias.getOrDefault(dia, BigDecimal.ZERO)));
            }
            series.put(moneda, puntos);
        });
        return series;
    }
}
