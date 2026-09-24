package com.tbridge.payments.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.payments.model.UfValue;
import com.tbridge.payments.repository.UfValueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UfServiceTest {

    @Mock
    private UfValueRepository valores;

    @InjectMocks
    private UfService uf;

    @Test
    void sin_la_uf_del_dia_el_cobro_se_detiene_en_vez_de_adivinar() {
        LocalDate hoy = LocalDate.of(2026, 9, 24);
        when(valores.findById(hoy)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> uf.delDia(hoy));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void los_pesos_se_redondean_al_entero() {
        assertEquals(1535247L, uf.aPesos(new BigDecimal("38.50"), new BigDecimal("39876.54")));
        assertEquals(19938L, uf.aPesos(new BigDecimal("0.50"), new BigDecimal("39876.54")));
    }

    @Test
    void la_carga_a_mano_guarda_con_dos_decimales_y_rechaza_negativos() {
        when(valores.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

        var guardado = uf.cargarAMano(LocalDate.of(2026, 9, 22), new BigDecimal("39876.5"));
        assertEquals(new BigDecimal("39876.50"), guardado.valor());
        assertEquals("manual", guardado.fuente());

        assertThrows(ApiException.class, () -> uf.cargarAMano(LocalDate.of(2026, 9, 22), new BigDecimal("-1")));
        verify(valores, never()).save(new UfValue(LocalDate.of(2026, 9, 22), new BigDecimal("-1"), "manual"));
    }
}
