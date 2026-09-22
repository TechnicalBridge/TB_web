package com.tbridge.debt.service;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.repo.DebtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    private final DebtRepository debts;
    private final DebtService servicio;

    public AnalyticsService(DebtRepository debts, DebtService servicio) {
        this.debts = debts;
        this.servicio = servicio;
    }

    public Map<String, Object> resumen(Organization acreedor) {
        List<Debt> cartera = debts.findByCreditorOrderByUpdatedAtDesc(acreedor);

        Map<String, BigDecimal[]> porMoneda = new LinkedHashMap<>();
        int activas = 0;
        int pagadas = 0;

        for (Debt deuda : cartera) {
            BigDecimal saldo = servicio.saldo(deuda);
            BigDecimal pagado = deuda.getOriginalAmount().subtract(saldo);
            BigDecimal[] acumulado = porMoneda.computeIfAbsent(
                    deuda.getCurrency().name(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acumulado[0] = acumulado[0].add(saldo);
            acumulado[1] = acumulado[1].add(pagado);
            if (deuda.getStatus() == Debt.Status.paid) {
                pagadas++;
            } else if (deuda.getStatus() != Debt.Status.withdrawn) {
                activas++;
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
        cuerpo.put("acreedor", acreedor.getTradeName());
        cuerpo.put("acreedorRut", acreedor.getRut());
        cuerpo.put("deudas", cartera.size());
        cuerpo.put("activas", activas);
        cuerpo.put("pagadas", pagadas);
        cuerpo.put("retiradas", debts.countByCreditorAndStatus(acreedor, Debt.Status.withdrawn));
        cuerpo.put("porMoneda", monedas);
        return cuerpo;
    }
}
