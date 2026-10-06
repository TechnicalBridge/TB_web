package com.tbridge.debt.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.client.AuthClient;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.repository.DebtEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccesoServiceTest {

    @Test
    void elCorreoSeReconoceSinExponerse() {
        assertEquals("fe**********@correo.cl", AccesoService.enmascarar("felipe.rojas@correo.cl"));
        assertEquals("***@x.cl", AccesoService.enmascarar("ab@x.cl"));
    }

    @Test
    void la_empresa_no_puede_reenviar_mas_de_lo_que_deja_la_ley() {
        DebtService debts = mock(DebtService.class);
        AuthClient auth = mock(AuthClient.class);
        LimiteDeContacto limite = mock(LimiteDeContacto.class);
        AccesoService servicio = new AccesoService(debts, mock(DebtEventRepository.class), auth, limite);
        JwtPrincipal empresa = new JwtPrincipal("1", "camila.reyes@apofyx.cl", "CREDITOR", "Camila", "77305118-6");
        Debtor deudor = new Debtor();
        deudor.setEmail("felipe.rojas@correo.cl");
        Debt deuda = new Debt();
        deuda.setDebtor(deudor);
        deuda.setStatus(Debt.Status.open);
        when(debts.requireVisible(empresa, 3L)).thenReturn(deuda);
        when(limite.permite(any(), any())).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> servicio.enviarCodigo(empresa, 3L));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertTrue(error.getMessage().startsWith("Ya se le escribio lo que permite la ley"), error.getMessage());
        verify(auth, never()).emitirCodigo(any());
    }
}
