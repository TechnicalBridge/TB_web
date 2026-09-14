package com.tbridge.debt.service;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.domain.AuditEvent;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Installment;
import com.tbridge.debt.dto.InstallmentPreview;
import com.tbridge.common.events.PagoExitosoEvent;
import com.tbridge.debt.dto.RepactPlan;
import com.tbridge.debt.repo.AuditEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import com.tbridge.debt.repo.InstallmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DebtService {

    private final DebtRepository debts;
    private final InstallmentRepository installments;
    private final AuditEventRepository audits;
    private final RepactationService repactation;

    public DebtService(
            DebtRepository debts,
            InstallmentRepository installments,
            AuditEventRepository audits,
            RepactationService repactation
    ) {
        this.debts = debts;
        this.installments = installments;
        this.audits = audits;
        this.repactation = repactation;
    }

    public List<Map<String, Object>> listFor(JwtPrincipal user) {
        List<Debt> rows = user.isCreditor()
                ? debts.findAllByOrderByCreatedAtDesc()
                : debts.findByDebtorEmailOrderByCreatedAtDesc(user.email());
        return rows.stream().map(this::toSummary).toList();
    }

    public Map<String, Object> getFor(JwtPrincipal user, String id) {
        Debt debt = requireVisible(user, id);
        Map<String, Object> body = toSummary(debt);
        body.put("cuotas", installments.findByDebtIdOrderByNumberAsc(id).stream().map(this::toInstallment).toList());
        body.put("auditoria", audits.findByDebtIdOrderByAtAsc(id).stream().map(this::toAudit).toList());
        return body;
    }

    public RepactPlan simulate(JwtPrincipal user, String id, int months) {
        Debt debt = requireVisible(user, id);
        return repactation.simulate(debt.getRemainingAmount(), months, LocalDate.now().plusMonths(1));
    }

    @Transactional
    public Map<String, Object> applyRepact(JwtPrincipal user, String id, int months) {
        if (user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La repactación la confirma el deudor");
        }
        Debt debt = requireVisible(user, id);
        if ("PAGADA".equals(debt.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "La deuda ya está pagada");
        }
        RepactPlan plan = repactation.simulate(debt.getRemainingAmount(), months, LocalDate.now().plusMonths(1));
        for (Installment pending : installments.findByDebtIdAndStatus(id, "PENDIENTE")) {
            installments.delete(pending);
        }
        for (InstallmentPreview preview : plan.cuotas()) {
            Installment inst = new Installment();
            inst.setId(UUID.randomUUID().toString());
            inst.setDebtId(id);
            inst.setNumber(preview.number());
            inst.setDueDate(preview.dueDate());
            inst.setAmount(preview.amount());
            inst.setStatus("PENDIENTE");
            installments.save(inst);
        }
        debt.setMonths(months);
        debt.setStatus("REPACTADA");
        debt.setDueDate(plan.cuotas().getLast().dueDate());
        debt.setUpdatedAt(Instant.now());
        debts.save(debt);
        audits.save(AuditEvent.of(id, "REPACTACION",
                "Nuevo plan de " + months + " cuotas. Cuota referencial " + plan.monthlyAmount() + " CLP"));
        return getFor(user, id);
    }

    @Transactional
    public Debt createDebt(String email, String name, String creditor, BigDecimal amount, LocalDate due, String description) {
        Debt debt = new Debt();
        debt.setId(UUID.randomUUID().toString());
        debt.setDebtorEmail(email.trim().toLowerCase());
        debt.setDebtorName(name);
        debt.setCreditorName(creditor);
        debt.setDescription(description);
        debt.setOriginalAmount(amount);
        debt.setRemainingAmount(amount);
        debt.setCurrency("CLP");
        debt.setStatus("ACTIVA");
        debt.setMonths(1);
        debt.setDueDate(due);
        Instant now = Instant.now();
        debt.setCreatedAt(now);
        debt.setUpdatedAt(now);
        debts.save(debt);

        Installment inst = new Installment();
        inst.setId(UUID.randomUUID().toString());
        inst.setDebtId(debt.getId());
        inst.setNumber(1);
        inst.setDueDate(due != null ? due : LocalDate.now().plusMonths(1));
        inst.setAmount(amount);
        inst.setStatus("PENDIENTE");
        installments.save(inst);
        audits.save(AuditEvent.of(debt.getId(), "ALTA", "Deuda ingresada por " + creditor + " · " + amount + " CLP"));
        return debt;
    }

    @Transactional
    public void onPagoExitoso(PagoExitosoEvent event) {
        if (event == null || event.debtId() == null) {
            return;
        }
        Debt debt = debts.findById(event.debtId()).orElse(null);
        if (debt == null) {
            return;
        }
        BigDecimal amount = event.amount() == null ? BigDecimal.ZERO : event.amount();
        if (event.installmentId() != null) {
            Installment inst = installments.findById(event.installmentId()).orElse(null);
            if (inst != null) {
                if ("PAGADA".equals(inst.getStatus())) {
                    return;
                }
                inst.setStatus("PAGADA");
                inst.setPaidAt(event.paidAt() == null ? Instant.now() : event.paidAt());
                installments.save(inst);
                amount = inst.getAmount();
            }
        }
        BigDecimal remaining = debt.getRemainingAmount().subtract(amount);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }
        debt.setRemainingAmount(remaining);
        debt.setStatus(remaining.compareTo(BigDecimal.ZERO) == 0 ? "PAGADA" : "PARCIAL");
        debt.setUpdatedAt(Instant.now());
        debts.save(debt);
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            for (Installment inst : installments.findByDebtIdAndStatus(debt.getId(), "PENDIENTE")) {
                inst.setStatus("PAGADA");
                inst.setPaidAt(event.paidAt() == null ? Instant.now() : event.paidAt());
                installments.save(inst);
            }
        }
        audits.save(AuditEvent.of(
                debt.getId(),
                "PAGO",
                "pago_exitoso " + amount + " CLP vía " + (event.gateway() == null ? "pasarela" : event.gateway())
                        + " (paymentId=" + event.paymentId() + ")"
        ));
    }

    public Debt requireVisible(JwtPrincipal user, String id) {
        Debt debt = debts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada"));
        if (!user.isCreditor() && (user.email() == null || !user.email().equalsIgnoreCase(debt.getDebtorEmail()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No puedes ver esta deuda");
        }
        return debt;
    }

    public Map<String, Object> toSummary(Debt debt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", debt.getId());
        map.put("debtorEmail", debt.getDebtorEmail());
        map.put("debtorName", debt.getDebtorName());
        map.put("creditorName", debt.getCreditorName());
        map.put("description", debt.getDescription());
        map.put("originalAmount", debt.getOriginalAmount());
        map.put("remainingAmount", debt.getRemainingAmount());
        map.put("currency", debt.getCurrency());
        map.put("status", debt.getStatus());
        map.put("months", debt.getMonths());
        map.put("dueDate", debt.getDueDate());
        map.put("createdAt", debt.getCreatedAt());
        map.put("updatedAt", debt.getUpdatedAt());
        map.put("paidAmount", debt.getOriginalAmount().subtract(debt.getRemainingAmount()));
        return map;
    }

    private Map<String, Object> toInstallment(Installment inst) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", inst.getId());
        map.put("number", inst.getNumber());
        map.put("dueDate", inst.getDueDate());
        map.put("amount", inst.getAmount());
        map.put("status", inst.getStatus());
        map.put("paidAt", inst.getPaidAt());
        return map;
    }

    private Map<String, Object> toAudit(AuditEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("action", event.getAction());
        map.put("detail", event.getDetail());
        map.put("at", event.getAt());
        return map;
    }
}
