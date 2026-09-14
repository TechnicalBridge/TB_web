package com.tbridge.debt.service;

import com.tbridge.debt.domain.AuditEvent;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.repo.AuditEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnalyticsService {

    private final DebtRepository debts;
    private final AuditEventRepository audits;

    public AnalyticsService(DebtRepository debts, AuditEventRepository audits) {
        this.debts = debts;
        this.audits = audits;
    }

    public Map<String, Object> summary() {
        List<Debt> all = debts.findAll();
        BigDecimal cartera = BigDecimal.ZERO;
        BigDecimal recaudado = BigDecimal.ZERO;
        int activas = 0;
        int pagadas = 0;
        Map<String, BigDecimal[]> byCreditor = new LinkedHashMap<>();
        for (Debt debt : all) {
            BigDecimal paid = debt.getOriginalAmount().subtract(debt.getRemainingAmount());
            cartera = cartera.add(debt.getRemainingAmount());
            recaudado = recaudado.add(paid);
            if ("PAGADA".equals(debt.getStatus())) {
                pagadas++;
            } else {
                activas++;
            }
            BigDecimal[] bucket = byCreditor.computeIfAbsent(debt.getCreditorName(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            bucket[0] = bucket[0].add(debt.getRemainingAmount());
            bucket[1] = bucket[1].add(paid);
        }
        BigDecimal origin = cartera.add(recaudado);
        BigDecimal rate = origin.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : recaudado.multiply(BigDecimal.valueOf(100)).divide(origin, 1, RoundingMode.HALF_UP);

        Map<String, BigDecimal> monthly = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (AuditEvent event : audits.findAll()) {
            if (!"PAGO".equals(event.getAction()) || event.getAt() == null) {
                continue;
            }
            String key = event.getAt().atZone(ZoneId.systemDefault()).toLocalDate().format(fmt);
            monthly.merge(key, BigDecimal.ONE, BigDecimal::add);
        }

        List<Map<String, Object>> acreedores = new ArrayList<>();
        byCreditor.forEach((name, vals) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("activo", vals[0]);
            row.put("recaudado", vals[1]);
            acreedores.add(row);
        });

        List<Map<String, Object>> serie = new ArrayList<>();
        monthly.forEach((mes, count) -> serie.add(Map.of("mes", mes, "eventosPago", count)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalCartera", cartera);
        body.put("totalRecaudado", recaudado);
        body.put("deudasActivas", activas);
        body.put("deudasPagadas", pagadas);
        body.put("tasaRecuperacion", rate);
        body.put("porAcreedor", acreedores);
        body.put("serieMensual", serie);
        return body;
    }
}
