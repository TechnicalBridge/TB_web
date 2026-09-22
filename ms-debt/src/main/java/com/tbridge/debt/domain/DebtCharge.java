package com.tbridge.debt.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El desglose con que llego la deuda: los meses de arriendo, las cuotas, las
 * boletas.
 *
 * <p>Es lo que el deudor ve en el portal para reconocer que esta pagando. No
 * se toca cuando acepta un plan de pago: para eso estan las cuotas.
 */
@Entity
@Table(name = "debt_charges")
public class DebtCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(nullable = false, length = 120)
    private String concept;

    @Column(length = 7)
    private String period;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Debt getDebt() { return debt; }
    public void setDebt(Debt debt) { this.debt = debt; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
