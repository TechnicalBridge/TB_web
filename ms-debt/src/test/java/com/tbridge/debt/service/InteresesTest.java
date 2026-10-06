package com.tbridge.debt.service;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtCharge;
import com.tbridge.debt.model.Installment;
import com.tbridge.debt.model.Repactation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La mora: interes simple sobre el capital vencido, por dia, desde el dia
 * siguiente a cada vencimiento.
 */
class InteresesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 6);

    private static Debt deuda(String tasa, Debt.Currency moneda) {
        Debt deuda = new Debt();
        deuda.setId(3L);
        deuda.setCurrency(moneda);
        deuda.setInterestRate(tasa == null ? null : new BigDecimal(tasa));
        return deuda;
    }

    private static DebtCharge cargo(String monto, LocalDate vence) {
        DebtCharge cargo = new DebtCharge();
        cargo.setAmount(new BigDecimal(monto));
        cargo.setDueDate(vence);
        return cargo;
    }

    private static Installment cuota(long id, String monto, LocalDate vence) {
        Installment cuota = new Installment();
        cuota.setId(id);
        cuota.setNumber((short) id);
        cuota.setAmount(new BigDecimal(monto));
        cuota.setDueDate(vence);
        return cuota;
    }

    private static Installment deConvenio(long id, String monto, String interes, LocalDate vence) {
        Installment cuota = cuota(id, monto, vence);
        cuota.setInterestAmount(new BigDecimal(interes));
        cuota.setRepactation(new Repactation());
        return cuota;
    }

    @Test
    void sin_tasa_no_hay_intereses() {
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda(null, Debt.Currency.CLP),
                List.of(cargo("300000", LocalDate.of(2026, 8, 5))),
                List.of(cuota(1, "300000", LocalDate.of(2026, 8, 5))), HOY);

        assertTrue(mora.isEmpty());
    }

    @Test
    void cada_mes_atrasado_crece_desde_su_propio_vencimiento() {
        //  Una sola cuota que vence con el ultimo mes, pero agosto lleva mas atraso que octubre.
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.CLP),
                List.of(cargo("300000", LocalDate.of(2026, 8, 5)),
                        cargo("300000", LocalDate.of(2026, 9, 5)),
                        cargo("300000", LocalDate.of(2026, 10, 5))),
                List.of(cuota(1, "900000", LocalDate.of(2026, 10, 5))), HOY);

        //  300.000 x 1,5% / 30 = $150 por dia: 62 dias de agosto, 31 de septiembre y 1 de octubre.
        assertEquals(new BigDecimal("14100"), mora.get(1L));
    }

    @Test
    void el_dia_que_vence_todavia_no_hay_mora() {
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.CLP),
                List.of(cargo("300000", HOY)), List.of(cuota(1, "300000", HOY)), HOY);

        assertEquals(new BigDecimal("0"), mora.get(1L));
    }

    @Test
    void una_cuota_de_convenio_crece_solo_sobre_su_capital() {
        //  La cuota trae 10.000 de interes del convenio: la mora corre sobre los 90.000 de capital.
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.CLP), List.of(),
                List.of(deConvenio(1, "100000", "10000", LocalDate.of(2026, 9, 6))), HOY);

        assertEquals(new BigDecimal("1350"), mora.get(1L));
    }

    @Test
    void la_cuota_aparte_de_un_convenio_cubre_el_mes_mas_nuevo() {
        //  Julio a septiembre se repactaron; octubre llego despues y se paga aparte.
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.CLP),
                List.of(cargo("300000", LocalDate.of(2026, 7, 5)),
                        cargo("300000", LocalDate.of(2026, 8, 5)),
                        cargo("300000", LocalDate.of(2026, 9, 5)),
                        cargo("300000", LocalDate.of(2026, 10, 1))),
                List.of(deConvenio(1, "300000", "0", LocalDate.of(2026, 11, 5)),
                        cuota(2, "300000", LocalDate.of(2026, 10, 1))), HOY);

        assertEquals(new BigDecimal("750"), mora.get(2L), "5 dias de octubre, no los de julio");
        assertEquals(new BigDecimal("0"), mora.get(1L), "la del convenio todavia no vence");
    }

    @Test
    void en_uf_se_redondea_a_centesimas() {
        //  UF 38,50 x 1,5% x 30/30 = UF 0,5775
        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.UF),
                List.of(cargo("38.50", LocalDate.of(2026, 9, 6))),
                List.of(cuota(1, "38.50", LocalDate.of(2026, 9, 6))), HOY);

        assertEquals(new BigDecimal("0.58"), mora.get(1L));
    }

    @Test
    void solo_las_cuotas_pendientes_tienen_mora() {
        Installment pagada = cuota(1, "300000", LocalDate.of(2026, 8, 5));
        pagada.setStatus(Installment.Status.paid);

        Map<Long, BigDecimal> mora = Intereses.deMora(deuda("1.5", Debt.Currency.CLP),
                List.of(cargo("300000", LocalDate.of(2026, 8, 5))), List.of(pagada), HOY);

        assertTrue(mora.isEmpty());
    }
}
