package com.tbridge.debt.config;

import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtCharge;
import com.tbridge.debt.model.DebtEvent;
import com.tbridge.debt.model.Debtor;
import com.tbridge.debt.model.Installment;
import com.tbridge.debt.model.Organization;
import com.tbridge.debt.repository.BatchRepository;
import com.tbridge.debt.repository.DebtChargeRepository;
import com.tbridge.debt.repository.DebtEventRepository;
import com.tbridge.debt.repository.DebtRepository;
import com.tbridge.debt.repository.DebtorRepository;
import com.tbridge.debt.repository.InstallmentRepository;
import com.tbridge.debt.repository.OrganizationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos de demostracion.
 *
 * <p>Es la misma cartera del ejemplo del contrato de integracion: Patrimonio
 * Inmuebles entrega tres arrendatarios morosos al corte del 18-09-2026, bajo
 * el mandato de APOFYX. Que sea la misma en los tres sistemas no es adorno:
 * permite seguir una deuda desde el contrato de arriendo hasta el pago sin
 * cambiar de historia a mitad de camino.
 *
 * <p>Solo siembra si la base esta vacia.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final OrganizationRepository organizations;
    private final DebtorRepository debtors;
    private final BatchRepository batches;
    private final DebtRepository debts;
    private final DebtChargeRepository charges;
    private final InstallmentRepository installments;
    private final DebtEventRepository events;

    public DataSeeder(
            OrganizationRepository organizations,
            DebtorRepository debtors,
            BatchRepository batches,
            DebtRepository debts,
            DebtChargeRepository charges,
            InstallmentRepository installments,
            DebtEventRepository events
    ) {
        this.organizations = organizations;
        this.debtors = debtors;
        this.batches = batches;
        this.debts = debts;
        this.charges = charges;
        this.installments = installments;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        if (organizations.count() > 0) {
            return;
        }

        Organization patrimonio = organizacion(
                "76418902-7", "Patrimonio Inmuebles SpA", "Patrimonio Inmuebles",
                Organization.Kind.creditor);
        Organization apofyx = organizacion(
                "77305118-6", "APOFYX SpA", "APOFYX", Organization.Kind.agency);

        Batch lote = new Batch();
        lote.setSender(apofyx);
        lote.setCreditor(patrimonio);
        lote.setExternalId("APX-2026-09-19-004");
        lote.setCutOff(LocalDate.of(2026, 9, 18));
        lote.setPayloadHash("0".repeat(64));
        lote.setReceivedCount(3);
        lote.setAcceptedCount(3);
        batches.save(lote);

        Debtor felipe = deudor("16482337-7", Debtor.Kind.person, "Felipe Rojas Munoz",
                "felipe.rojas@correo.cl", "+56987654321");
        Debtor valentina = deudor("18905214-6", Debtor.Kind.person, "Valentina Soto Pizarro",
                "valentina.soto@correo.cl", "+56912348765");
        //  Sin telefono: el codigo de acceso le llegara por un solo canal.
        Debtor nandu = deudor("76991245-2", Debtor.Kind.company, "Comercial Nandu SpA",
                "administracion@nandu.cl", null);

        deuda(lote, patrimonio, felipe, "CTR-2025-014", Debt.Currency.CLP, "Arriendo mensual",
                "{\"contrato\":\"CTR-2025-014\",\"propiedad\":\"Depto 1204, Av. Irarrazaval 2450, Nunoa\"}",
                List.of(
                        cargo("Arriendo agosto", "2026-08", 520000, LocalDate.of(2026, 8, 5)),
                        cargo("Arriendo septiembre", "2026-09", 520000, LocalDate.of(2026, 9, 5))));

        deuda(lote, patrimonio, valentina, "CTR-2026-031", Debt.Currency.CLP, "Arriendo mensual",
                "{\"contrato\":\"CTR-2026-031\",\"propiedad\":\"Depto 305, Los Leones 1180, Providencia\"}",
                List.of(cargo("Arriendo septiembre", "2026-09", 410000, LocalDate.of(2026, 9, 5))));

        deuda(lote, patrimonio, nandu, "CTR-2024-007", Debt.Currency.UF, "Arriendo local comercial",
                "{\"contrato\":\"CTR-2024-007\",\"propiedad\":\"Local 3, Av. Italia 1320, Providencia\"}",
                List.of(
                        cargo("Arriendo julio", "2026-07", 38.5, LocalDate.of(2026, 7, 5)),
                        cargo("Arriendo agosto", "2026-08", 38.5, LocalDate.of(2026, 8, 5)),
                        cargo("Arriendo septiembre", "2026-09", 38.5, LocalDate.of(2026, 9, 5))));
    }

    private Organization organizacion(String rut, String razon, String fantasia, Organization.Kind tipo) {
        Organization organizacion = new Organization();
        organizacion.setRut(rut);
        organizacion.setLegalName(razon);
        organizacion.setTradeName(fantasia);
        organizacion.setKind(tipo);
        return organizations.save(organizacion);
    }

    private Debtor deudor(String rut, Debtor.Kind tipo, String nombre, String correo, String telefono) {
        Debtor deudor = new Debtor();
        deudor.setRut(rut);
        deudor.setKind(tipo);
        deudor.setFullName(nombre);
        deudor.setEmail(correo);
        deudor.setPhone(telefono);
        return debtors.save(deudor);
    }

    private record CargoSemilla(String concepto, String periodo, BigDecimal monto, LocalDate vence) {}

    private CargoSemilla cargo(String concepto, String periodo, double monto, LocalDate vence) {
        return new CargoSemilla(concepto, periodo, BigDecimal.valueOf(monto), vence);
    }

    /**
     * Una deuda con sus cargos y su cuota unica.
     *
     * <p>Sin repactar, una deuda tiene una sola cuota por el total: es lo que
     * el deudor paga si decide pagarla de una vez.
     */
    private void deuda(Batch lote, Organization acreedor, Debtor deudor, String idExterno,
                       Debt.Currency moneda, String concepto, String refs,
                       List<CargoSemilla> cargos) {
        BigDecimal total = cargos.stream()
                .map(CargoSemilla::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Debt deuda = new Debt();
        deuda.setCreditor(acreedor);
        deuda.setDebtor(deudor);
        deuda.setExternalId(idExterno);
        deuda.setCurrency(moneda);
        deuda.setConcept(concepto);
        deuda.setRefs(refs);
        deuda.setOriginalAmount(total);
        deuda.setFirstBatch(lote);
        deuda.setLastBatch(lote);
        debts.save(deuda);

        for (CargoSemilla semilla : cargos) {
            DebtCharge cargo = new DebtCharge();
            cargo.setDebt(deuda);
            cargo.setConcept(semilla.concepto());
            cargo.setPeriod(semilla.periodo());
            cargo.setAmount(semilla.monto());
            cargo.setDueDate(semilla.vence());
            charges.save(cargo);
        }

        Installment cuota = new Installment();
        cuota.setDebt(deuda);
        cuota.setNumber((short) 1);
        cuota.setDueDate(cargos.getLast().vence());
        cuota.setAmount(total);
        installments.save(cuota);

        events.save(DebtEvent.de(deuda, DebtEvent.Type.registered, DebtEvent.Actor.agency)
                .conMonto(total, moneda)
                .conReferencia(lote.getExternalId()));
    }
}
