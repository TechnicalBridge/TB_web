package com.tbridge.debt.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cada plan de pago que el deudor acepto.
 *
 * <p>No se sobreescribe el anterior: se marca `supersededAt` y se crea otro.
 * Un plan aceptado es un compromiso con fecha, y borrarlo seria perder por que
 * las cuotas son las que son.
 */
@Entity
@Table(name = "repactations")
public class Repactation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false)
    private Short months;

    @Column(name = "monthly_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Debt.Currency currency;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt = Instant.now();

    @Column(name = "superseded_at")
    private Instant supersededAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Debt getDebt() { return debt; }
    public void setDebt(Debt debt) { this.debt = debt; }
    public Short getMonths() { return months; }
    public void setMonths(Short months) { this.months = months; }
    public BigDecimal getMonthlyAmount() { return monthlyAmount; }
    public void setMonthlyAmount(BigDecimal monthlyAmount) { this.monthlyAmount = monthlyAmount; }
    public Debt.Currency getCurrency() { return currency; }
    public void setCurrency(Debt.Currency currency) { this.currency = currency; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant supersededAt) { this.supersededAt = supersededAt; }
}
