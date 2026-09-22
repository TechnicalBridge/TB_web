package com.tbridge.debt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccesoServiceTest {

    @Test
    void elCorreoSeReconoceSinExponerse() {
        assertEquals("fe**********@correo.cl", AccesoService.enmascarar("felipe.rojas@correo.cl"));
        assertEquals("***@x.cl", AccesoService.enmascarar("ab@x.cl"));
    }
}
