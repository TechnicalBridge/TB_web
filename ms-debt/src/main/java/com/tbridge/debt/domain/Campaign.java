package com.tbridge.debt.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * La estrategia de contacto que define la agencia.
 *
 * <p>DataBridge la ejecuta pero no la decide: los canales, los intentos y la
 * cadencia son de la agencia. Es el reparto de responsabilidades de la seccion
 * 13.6 del documento de APOFYX.
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    public enum Status { running, paused, finished }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Organization agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creditor_id", nullable = false)
    private Organization creditor;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false, columnDefinition = "json")
    private String channels = "[]";

    @Column(nullable = false)
    private Short attempts = 3;

    @Column(name = "cadence_days", columnDefinition = "json")
    private String cadenceDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.running;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Organization getAgency() { return agency; }
    public void setAgency(Organization agency) { this.agency = agency; }
    public Organization getCreditor() { return creditor; }
    public void setCreditor(Organization creditor) { this.creditor = creditor; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }
    public Short getAttempts() { return attempts; }
    public void setAttempts(Short attempts) { this.attempts = attempts; }
    public String getCadenceDays() { return cadenceDays; }
    public void setCadenceDays(String cadenceDays) { this.cadenceDays = cadenceDays; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
