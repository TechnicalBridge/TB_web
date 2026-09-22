package com.tbridge.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * El valor de la UF de un dia.
 *
 * <p>Se guarda en vez de consultarse al vuelo por dos razones: un pago tiene
 * que poder reconstruirse anos despues, y si la fuente esta caida el cobro no
 * puede detenerse.
 *
 * <p>De donde se trae —Banco Central o CMF— sigue siendo la decision I10 del
 * contrato, abierta. `source` esta para poder cambiar de fuente sin perder lo
 * ya guardado.
 */
@Entity
@Table(name = "uf_values")
public class UfValue {

    /**
     * El dia EN CHILE, no el del servidor.
     *
     * <p>No es un detalle: el contenedor de MySQL corre en UTC, y entre las
     * 21:00 y la medianoche chilena la fecha UTC ya es la del dia siguiente.
     * Un cargador que use {@code CURDATE()} de la base guardaria el valor con
     * un dia de adelanto durante tres horas cada dia, y el cobro en UF se
     * caeria justo en esa ventana por no encontrar el valor del dia.
     */
    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    public UfValue() {
    }

    public UfValue(LocalDate day, BigDecimal value, String source) {
        this.day = day;
        this.value = value;
        this.source = source;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
