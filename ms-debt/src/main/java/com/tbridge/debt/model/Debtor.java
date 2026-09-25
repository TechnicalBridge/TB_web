package com.tbridge.debt.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Quien debe.
 *
 * <p>Una fila por RUT aunque deba a varios acreedores: es la misma persona. El
 * portal se lo muestra todo junto cuando entra con su codigo, y por eso el
 * deudor no puede estar repetido.
 *
 * <p>El modelo anterior lo guardaba como correo y nombre sueltos dentro de
 * cada deuda: si cambiaba de correo, sus deudas quedaban huerfanas.
 */
@Entity
@Table(name = "debtors")
public class Debtor {

    public enum Kind { person, company }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12, unique = true)
    private String rut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind = Kind.person;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Si quiere el correo que le recuerda una cuota por vencer. */
    @Column(nullable = false)
    private boolean reminders = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isReminders() { return reminders; }
    public void setReminders(boolean reminders) { this.reminders = reminders; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
