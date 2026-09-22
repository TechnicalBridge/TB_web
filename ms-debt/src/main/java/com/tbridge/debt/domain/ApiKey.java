package com.tbridge.debt.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * La credencial con la que un acreedor o una agencia entrega cartera.
 *
 * <p>No se guarda la clave, se guarda su huella SHA-256. Si alguien se lleva
 * esta tabla no se lleva las claves, y DataBridge tampoco puede recordarsela a
 * nadie: si se pierde, se emite otra.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Se carga de inmediato, no en diferido.
     *
     * Nadie autentica una clave sin querer saber de quien es, y devolverla
     * como proxy hacia que reventara al tocarla fuera de la transaccion que la
     * leyo.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "key_hash", nullable = false, columnDefinition = "char(64)")
    private String keyHash;

    /** Para poder decir cual es sin revelarla. */
    @Column(nullable = false, length = 12)
    private String prefix;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean vigente() {
        return revokedAt == null;
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
