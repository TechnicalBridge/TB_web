package com.tbridge.common.jwt;

public record JwtPrincipal(String id, String email, String role, String name) {

    public boolean isCreditor() {
        return "CREDITOR".equalsIgnoreCase(role);
    }

    public boolean isDebtor() {
        return "DEBTOR".equalsIgnoreCase(role);
    }
}
