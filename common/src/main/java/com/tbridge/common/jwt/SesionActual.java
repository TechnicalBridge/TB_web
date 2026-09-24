package com.tbridge.common.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Quien hizo la peticion en curso.
 *
 * <p>Lo usan los ensambladores de HATEOAS para ofrecer solo los enlaces que
 * quien pregunta puede seguir: el deudor ve "pagar" y "repactar"; la empresa,
 * "enviar codigo". La autorizacion de verdad la hace cada servicio igual; esto
 * solo evita ofrecer un camino que va a terminar en 403.
 */
public final class SesionActual {

    private SesionActual() {
    }

    public static Optional<JwtPrincipal> principal() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof JwtPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public static boolean esDeudor() {
        return principal().map(JwtPrincipal::isDebtor).orElse(false);
    }

    public static boolean esEmpresa() {
        return principal().map(JwtPrincipal::isCreditor).orElse(false);
    }
}
