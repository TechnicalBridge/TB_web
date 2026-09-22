package com.tbridge.common.jwt;

/**
 * Quien viene en el token.
 *
 * <p>`rut` es lo que permite que un acreedor vea SOLO su cartera y que un
 * deudor vea solo lo suyo. Mientras ms-auth no lo emita viene en null, y los
 * servicios caen al correo como identificador de transicion.
 */
public record JwtPrincipal(String id, String email, String role, String name, String rut) {

    /** Para tokens que todavia no traen RUT. */
    public JwtPrincipal(String id, String email, String role, String name) {
        this(id, email, role, name, null);
    }

    public boolean isCreditor() {
        return "CREDITOR".equalsIgnoreCase(role);
    }

    public boolean isDebtor() {
        return "DEBTOR".equalsIgnoreCase(role);
    }
}
