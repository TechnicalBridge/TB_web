package com.tbridge.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Quien entro, cuando y por donde.
 *
 * <p>Sirve para dos cosas distintas: para que el acreedor sepa que su deudor
 * si vio la deuda —es la metrica que reemplaza al clic en el enlace— y para
 * detectar a alguien probando codigos.
 *
 * <p>Se guarda la huella de la IP, no la IP: alcanza para contar intentos
 * desde un mismo origen sin registrar desde donde se conecta alguien que solo
 * venia a pagar.
 */
@Entity
@Table(name = "access_log")
public class AccessLog {

    public enum Method { code, magic_link }

    public enum Outcome { granted, expired, invalid, exhausted }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "debtor_rut", length = 12)
    private String debtorRut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Outcome outcome;

    @Column(name = "ip_hash", columnDefinition = "char(64)")
    private String ipHash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public static AccessLog de(String rut, Method metodo, Outcome resultado, String ipHash) {
        AccessLog registro = new AccessLog();
        registro.debtorRut = rut;
        registro.method = metodo;
        registro.outcome = resultado;
        registro.ipHash = ipHash;
        return registro;
    }

    public Long getId() { return id; }
    public String getDebtorRut() { return debtorRut; }
    public Method getMethod() { return method; }
    public Outcome getOutcome() { return outcome; }
    public String getIpHash() { return ipHash; }
    public Instant getOccurredAt() { return occurredAt; }
}
