package com.tbridge.auth.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * El respaldo cuando el codigo no funciona, y para el personal de una empresa.
 *
 * <p>Se conserva con una advertencia: es justo el patron que el documento de
 * APOFYX critica, porque el destino lo elige quien manda el mensaje. Por eso
 * vive mucho menos que un codigo y se usa solo cuando alguien lo pide.
 */
@Entity
@Table(name = "magic_links")
public class MagicLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "debtor_rut", length = 12)
    private String debtorRut;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean vigente() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getDebtorRut() { return debtorRut; }
    public void setDebtorRut(String debtorRut) { this.debtorRut = debtorRut; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
