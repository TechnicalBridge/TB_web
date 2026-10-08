package com.tbridge.payments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Un intento de pago.
 *
 * Se crea al iniciar el pago, no al confirmarlo: un pago que fracasa tambien
 * es informacion, y es la unica forma de saber cuanta gente llego hasta la
 * pasarela y no pudo.
 *
 * <p><b>El estado es una proyeccion, no la verdad.</b> La historia esta en
 * {@link PaymentEvent}, que solo se inserta. Esta columna guarda el ultimo
 * estado para poder consultarlo sin recorrer el libro, y se escribe en la
 * misma transaccion que inserta el evento.
 *
 * <p><b>debtId e installmentId son de otro servicio.</b> Viven en tb_debt, que
 * es otra base: aca no hay clave foranea, y quien los valida es ms-debt.
 */
@Entity
@Table(name = "payments")
public class Payment {

    public enum Currency { CLP, UF }

    public enum Gateway { mercadopago, khipu, webpay }

    /**
     * {@code duplicated}: la pasarela cobro, pero las cuotas ya las habia
     * pagado otro pago. No se abona: hay que devolverlo en la pasarela.
     */
    public enum Status { created, authorized, paid, failed, expired, refunded, duplicated }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "debt_id", nullable = false)
    private Long debtId;

    @Column(name = "installment_id")
    private Long installmentId;

    /**
     * Las cuotas que cubre, separadas por coma, tal como las cobro ms-debt.
     * Con esto se sabe si dos pagos se pisan: si otro pago ya cubrio alguna,
     * este es un pago duplicado.
     */
    @Column(name = "installment_ids", length = 2000)
    private String installmentIds;

    /** El deudor se identifica por RUT, no por correo: el correo cambia. */
    @Column(name = "debtor_rut", nullable = false, length = 12)
    private String debtorRut;

    @Column(name = "creditor_rut", nullable = false, length = 12)
    private String creditorRut;

    /** En la moneda de la deuda. Una deuda en UF se cobra en UF. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /**
     * Cuanto de amount es mora, si la deuda genera intereses. Lo dice ms-debt
     * al abrir el cobro y queda fijo, como los pesos de una deuda en UF.
     */
    @Column(name = "interest_amount", precision = 18, scale = 2)
    private BigDecimal interestAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    /** Lo que efectivamente se cobro en pesos. */
    @Column(name = "amount_clp")
    private Long amountClp;

    /**
     * El valor de la UF con que se convirtio. Sin el, nadie puede reconstruir
     * despues por que UF 38,5 fueron esos pesos y no otros.
     */
    @Column(name = "uf_value", precision = 12, scale = 2)
    private BigDecimal ufValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gateway gateway;

    /**
     * El identificador que le puso la pasarela. Junto a `gateway` forma el
     * unico que hace que un webhook reintentado no cobre dos veces.
     */
    @Column(name = "gateway_txn_id", length = 80)
    private String gatewayTxnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.created;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Cuando se llevo al deudor a la pasarela. En Webpay, para no mandar el mismo token dos veces. */
    @Column(name = "redirected_at")
    private Instant redirectedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDebtId() {
        return debtId;
    }

    public void setDebtId(Long debtId) {
        this.debtId = debtId;
    }

    public Long getInstallmentId() {
        return installmentId;
    }

    public void setInstallmentId(Long installmentId) {
        this.installmentId = installmentId;
    }

    /** Las cuotas que cubre. En un pago de antes de guardarlas, su cuota si era una. */
    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public void setInterestAmount(BigDecimal interestAmount) {
        this.interestAmount = interestAmount;
    }

    public Set<Long> cuotas() {
        if (installmentIds == null || installmentIds.isBlank()) {
            return installmentId == null ? Set.of() : Set.of(installmentId);
        }
        return Arrays.stream(installmentIds.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                .map(Long::valueOf).collect(Collectors.toSet());
    }

    public void setCuotas(List<Long> ids) {
        this.installmentIds = ids == null || ids.isEmpty() ? null
                : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public String getDebtorRut() {
        return debtorRut;
    }

    public void setDebtorRut(String debtorRut) {
        this.debtorRut = debtorRut;
    }

    public String getCreditorRut() {
        return creditorRut;
    }

    public void setCreditorRut(String creditorRut) {
        this.creditorRut = creditorRut;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Long getAmountClp() {
        return amountClp;
    }

    public void setAmountClp(Long amountClp) {
        this.amountClp = amountClp;
    }

    public BigDecimal getUfValue() {
        return ufValue;
    }

    public void setUfValue(BigDecimal ufValue) {
        this.ufValue = ufValue;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public String getGatewayTxnId() {
        return gatewayTxnId;
    }

    public void setGatewayTxnId(String gatewayTxnId) {
        this.gatewayTxnId = gatewayTxnId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getRedirectedAt() {
        return redirectedAt;
    }

    public void setRedirectedAt(Instant redirectedAt) {
        this.redirectedAt = redirectedAt;
    }
}
