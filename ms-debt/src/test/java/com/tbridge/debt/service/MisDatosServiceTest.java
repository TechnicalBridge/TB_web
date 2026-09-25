package com.tbridge.debt.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.dto.request.MisDatosRequest;
import com.tbridge.debt.dto.response.MisDatosResponse;
import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.DebtRepository;
import com.tbridge.debt.repository.DebtorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lo que DataBridge sabe del deudor: a medias, y diciendo de parte de quien. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MisDatosServiceTest {

    @Mock private DebtService debts;
    @Mock private DebtRepository deudas;
    @Mock private DebtorRepository debtors;

    private MisDatosService servicio;
    private Debtor felipe;
    private final JwtPrincipal sesion = new JwtPrincipal("16482337-7", null, "DEBTOR", null, "16482337-7");

    private static Organization organizacion(long id, String nombre) {
        Organization org = new Organization();
        org.setId(id);
        org.setTradeName(nombre);
        org.setRut(id == 1 ? "76418902-7" : "77305118-6");
        return org;
    }

    @BeforeEach
    void preparar() {
        servicio = new MisDatosService(debts, deudas, debtors, 3);
        felipe = new Debtor();
        felipe.setRut("16482337-7");
        felipe.setFullName("Felipe Rojas Muñoz");
        felipe.setEmail("felipe.rojas@correo.cl");
        felipe.setPhone("+56987654321");

        Organization patrimonio = organizacion(1, "Patrimonio Inmuebles");
        Batch lote = new Batch();
        lote.setSender(organizacion(2, "APOFYX"));
        Debt deuda = new Debt();
        deuda.setCreditor(patrimonio);
        deuda.setLastBatch(lote);
        when(debts.deudorDe(sesion)).thenReturn(felipe);
        when(deudas.findByDebtorOrderByUpdatedAtDesc(felipe)).thenReturn(List.of(deuda));
    }

    @Test
    void el_correo_y_el_telefono_van_a_medias_y_se_dice_quien_cobra() {
        MisDatosResponse datos = servicio.ver(sesion);

        assertEquals("fe**********@correo.cl", datos.correo());
        assertEquals("+569****4321", datos.telefono());
        assertEquals(3, datos.diasAntes());
        MisDatosResponse.Acreedor acreedor = datos.acreedores().getFirst();
        assertEquals("Patrimonio Inmuebles", acreedor.nombre());
        assertEquals("APOFYX", acreedor.cobraPorSuCuenta());
    }

    @Test
    void sin_telefono_no_se_inventa_uno() {
        felipe.setPhone(null);
        assertNull(servicio.ver(sesion).telefono());
    }

    @Test
    void el_deudor_apaga_los_recordatorios() {
        MisDatosResponse datos = servicio.cambiar(sesion, new MisDatosRequest(false));

        assertFalse(datos.recordatorios());
        verify(debtors).save(felipe);
    }

    @Test
    void una_empresa_no_tiene_mis_datos() {
        JwtPrincipal empresa = new JwtPrincipal("1", "camila.reyes@apofyx.cl", "CREDITOR", "Camila", "77305118-6");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> servicio.ver(empresa)).getStatus());
    }
}
