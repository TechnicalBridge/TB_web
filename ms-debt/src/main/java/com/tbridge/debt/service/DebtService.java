package com.tbridge.debt.service;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtCharge;
import com.tbridge.debt.domain.DebtEvent;
import com.tbridge.debt.domain.Debtor;
import com.tbridge.debt.domain.Installment;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.domain.Repactation;
import com.tbridge.debt.dto.InstallmentPreview;
import com.tbridge.debt.dto.RepactPlan;
import com.tbridge.debt.integracion.EventosService;
import com.tbridge.debt.repo.DebtChargeRepository;
import com.tbridge.debt.repo.DebtEventRepository;
import com.tbridge.debt.repo.DebtRepository;
import com.tbridge.debt.repo.DebtorRepository;
import com.tbridge.debt.repo.InstallmentRepository;
import com.tbridge.debt.repo.OrganizationRepository;
import com.tbridge.debt.repo.RepactationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las deudas.
 *
 * <p><b>Lo que cambia respecto del modelo anterior.</b> Toda consulta de
 * acreedor pasa por su organizacion. Antes esto era {@code findAll()} para
 * cualquiera con rol CREDITOR, asi que todos veian las carteras de todos; no
 * era un descuido del codigo, era que la deuda no tenia a quien pertenecer.
 *
 * <p><b>El saldo se calcula.</b> Sale de las cuotas pendientes, nunca de una
 * columna guardada: un total almacenado al lado de sus partes termina
 * discrepando de ellas.
 */
@Service
//  open-in-view esta apagado a proposito: la sesion no sigue abierta mientras
//  se serializa la respuesta. Por eso las lecturas tambien necesitan su
//  transaccion, o las relaciones perezosas revientan al tocarlas.
@Transactional(readOnly = true)
public class DebtService {

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final DebtRepository debts;
    private final DebtorRepository debtors;
    private final OrganizationRepository organizations;
    private final DebtChargeRepository charges;
    private final InstallmentRepository installments;
    private final RepactationRepository repactations;
    private final DebtEventRepository events;
    private final RepactationService repactation;
    private final EventosService eventos;

    public DebtService(
            DebtRepository debts,
            DebtorRepository debtors,
            OrganizationRepository organizations,
            DebtChargeRepository charges,
            InstallmentRepository installments,
            RepactationRepository repactations,
            DebtEventRepository events,
            RepactationService repactation,
            EventosService eventos
    ) {
        this.debts = debts;
        this.debtors = debtors;
        this.organizations = organizations;
        this.charges = charges;
        this.installments = installments;
        this.repactations = repactations;
        this.events = events;
        this.repactation = repactation;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------
    //  Quien esta preguntando
    // ------------------------------------------------------------------

    /**
     * El acreedor detras del token.
     *
     * <p>Se resuelve por RUT. Sin RUT en el token no hay forma de acotar la
     * cartera, y devolver "todas" seria repetir la fuga que este redisenio
     * vino a cerrar: antes eso, un error.
     */
    private Organization acreedorDe(JwtPrincipal user) {
        if (user == null || user.rut() == null || user.rut().isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "El token no dice de que empresa eres: no se puede mostrar una cartera");
        }
        return organizations.findByRut(user.rut())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "Esa empresa no esta registrada en DataBridge"));
    }

    /**
     * El deudor detras del token, por RUT.
     *
     * <p>Hubo un respaldo por correo mientras ms-auth emitia tokens sin RUT.
     * Ya no los emite, y el correo no es una identidad verificada: se quito.
     */
    private Debtor deudorDe(JwtPrincipal user) {
        if (user == null || user.rut() == null || user.rut().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La sesion no identifica al deudor");
        }
        return debtors.findByRut(user.rut())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No hay deudas a tu nombre"));
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    @Transactional
    public List<Map<String, Object>> listFor(JwtPrincipal user) {
        if (user.isCreditor()) {
            return debts.carteraDe(acreedorDe(user)).stream().map(this::toSummary).toList();
        }
        List<Debt> filas = debts.findByDebtorOrderByUpdatedAtDesc(deudorDe(user));
        filas.forEach(this::anotarIngreso);
        return filas.stream().map(this::toSummary).toList();
    }

    /**
     * El deudor entro a ver su deuda. Se anota una vez al dia por deuda: es lo
     * que la agencia mide como "ingresos al portal" y, con codigo de acceso,
     * reemplaza al clic en el enlace que ya no existe.
     */
    private void anotarIngreso(Debt deuda) {
        Instant desdeHoy = LocalDate.now(CHILE).atStartOfDay(CHILE).toInstant();
        if (!events.existsByDebtAndTypeAndOccurredAtAfter(deuda, DebtEvent.Type.portal_entered, desdeHoy)) {
            events.save(DebtEvent.de(deuda, DebtEvent.Type.portal_entered, DebtEvent.Actor.debtor));
        }
    }

    public Map<String, Object> getFor(JwtPrincipal user, Long id) {
        Debt deuda = requireVisible(user, id);
        Map<String, Object> cuerpo = toSummary(deuda);
        cuerpo.put("cargos", charges.findByDebtOrderByDueDateAsc(deuda).stream()
                .map(this::cargoPublico).toList());
        cuerpo.put("cuotas", installments.findByDebtOrderByNumberAsc(deuda).stream()
                .map(this::cuotaPublica).toList());
        cuerpo.put("historia", events.findByDebtOrderByOccurredAtAsc(deuda).stream()
                .map(this::eventoPublico).toList());
        return cuerpo;
    }

    /**
     * La deuda, si a quien pregunta le corresponde verla.
     *
     * <p>El acreedor solo ve las suyas; el deudor, solo las propias.
     */
    public Debt requireVisible(JwtPrincipal user, Long id) {
        Debt deuda = debts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada"));

        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }
        if (user.isCreditor()) {
            if (!opera(acreedorDe(user), deuda)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Esa deuda no es de tu cartera");
            }
            return deuda;
        }
        if (!deuda.getDebtor().getId().equals(deudorDe(user).getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No puedes ver esta deuda");
        }
        return deuda;
    }

    /** Lo que se debe hoy: la suma de las cuotas pendientes. */
    /** La organizacion detras de la sesion de una empresa. */
    public Organization organizacionDe(JwtPrincipal user) {
        if (user == null || !user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo una empresa carga cartera");
        }
        return acreedorDe(user);
    }

    /** La misma regla que {@code DebtRepository.carteraDe}, para una deuda suelta. */
    private static boolean opera(Organization organizacion, Debt deuda) {
        return deuda.getCreditor().getId().equals(organizacion.getId())
                || deuda.getLastBatch().getSender().getId().equals(organizacion.getId());
    }

    public BigDecimal saldo(Debt deuda) {
        return installments.findByDebtAndStatus(deuda, Installment.Status.pending).stream()
                .map(Installment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    //  Repactacion
    // ------------------------------------------------------------------

    public RepactPlan simulate(JwtPrincipal user, Long id, int months) {
        Debt deuda = requireVisible(user, id);
        return repactation.simulate(saldo(deuda), deuda.getCurrency(), months, LocalDate.now().plusMonths(1));
    }

    /**
     * El deudor acepta un plan.
     *
     * <p>Las cuotas pendientes se ANULAN, no se borran: un compromiso que
     * existio deja rastro. Y el plan anterior queda marcado como reemplazado,
     * por la misma razon.
     */
    @Transactional
    public Map<String, Object> applyRepact(JwtPrincipal user, Long id, int months) {
        if (user != null && user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La repactacion la acepta el deudor");
        }
        Debt deuda = requireVisible(user, id);
        if (deuda.getStatus() == Debt.Status.paid) {
            throw new ApiException(HttpStatus.CONFLICT, "La deuda ya esta pagada");
        }
        if (deuda.getStatus() == Debt.Status.withdrawn) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El acreedor retiro esta deuda de la cobranza");
        }

        RepactPlan plan = repactation.simulate(saldo(deuda), deuda.getCurrency(), months, LocalDate.now().plusMonths(1));

        for (Repactation anterior : repactations.findByDebtAndSupersededAtIsNull(deuda)) {
            anterior.setSupersededAt(Instant.now());
            repactations.save(anterior);
        }
        Repactation nueva = new Repactation();
        nueva.setDebt(deuda);
        nueva.setMonths((short) months);
        nueva.setMonthlyAmount(plan.monthlyAmount());
        nueva.setCurrency(deuda.getCurrency());
        repactations.save(nueva);

        for (Installment pendiente : installments.findByDebtAndStatus(deuda, Installment.Status.pending)) {
            pendiente.setStatus(Installment.Status.void_);
            installments.save(pendiente);
        }

        short numero = siguienteNumero(deuda);
        for (InstallmentPreview previa : plan.cuotas()) {
            Installment cuota = new Installment();
            cuota.setDebt(deuda);
            cuota.setRepactation(nueva);
            cuota.setNumber(numero++);
            cuota.setDueDate(previa.dueDate());
            cuota.setAmount(previa.amount());
            cuota.setStatus(Installment.Status.pending);
            installments.save(cuota);
        }

        deuda.setStatus(Debt.Status.repacted);
        deuda.setUpdatedAt(Instant.now());
        debts.save(deuda);

        events.save(DebtEvent.de(deuda, DebtEvent.Type.repacted, DebtEvent.Actor.debtor)
                .conMonto(plan.monthlyAmount(), deuda.getCurrency())
                .conReferencia(months + " cuotas"));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("cuotas", months);
        datos.put("monto_cuota", EventosService.monto(plan.monthlyAmount(), deuda.getCurrency()));
        datos.put("moneda", deuda.getCurrency().name());
        datos.put("primera_cuota", plan.cuotas().get(0).dueDate().toString());
        eventos.publicar(deuda, EventosService.REPACTACION_ACEPTADA, datos, Instant.now());

        return getFor(user, id);
    }

    /** Las cuotas se numeran corrido, para que dos planes no choquen. */
    private short siguienteNumero(Debt deuda) {
        return (short) (installments.findByDebtOrderByNumberAsc(deuda).stream()
                .mapToInt(Installment::getNumber).max().orElse(0) + 1);
    }

    // ------------------------------------------------------------------
    //  Lo que ms-payments necesita y lo que avisa
    // ------------------------------------------------------------------

    /**
     * Cuanto se debe y a quien, para que ms-payments no le crea al navegador.
     *
     * <p>Antes el monto a cobrar venia en el cuerpo de la peticion de pago:
     * quien supiera el id de una deuda podia pagar un peso y darla por
     * saldada.
     */
    public Map<String, Object> snapshotInterno(Long debtId, Long installmentId) {
        Debt deuda = debts.findById(debtId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada"));

        BigDecimal monto;
        Long cuotaId = installmentId;
        if (installmentId != null) {
            Installment cuota = installments.findById(installmentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cuota no encontrada"));
            if (!cuota.getDebt().getId().equals(deuda.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Esa cuota no es de esa deuda");
            }
            if (cuota.getStatus() != Installment.Status.pending) {
                throw new ApiException(HttpStatus.CONFLICT, "Esa cuota no esta pendiente");
            }
            monto = cuota.getAmount();
        } else {
            monto = saldo(deuda);
            cuotaId = installments.findByDebtAndStatus(deuda, Installment.Status.pending).stream()
                    .min((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                    .map(Installment::getId).orElse(null);
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa deuda no tiene saldo por pagar");
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("debtId", deuda.getId());
        cuerpo.put("creditorRut", deuda.getCreditor().getRut());
        cuerpo.put("debtorRut", deuda.getDebtor().getRut());
        cuerpo.put("currency", deuda.getCurrency().name());
        cuerpo.put("amount", monto);
        cuerpo.put("installmentId", cuotaId);
        return cuerpo;
    }

    /**
     * Un pago confirmado por ms-payments.
     *
     * <p>Idempotente a proposito: el aviso se entrega "al menos una vez", asi
     * que el mismo pago puede llegar dos veces y la segunda no debe abonar de
     * nuevo. Se reconoce por la referencia de la pasarela, que ya quedo
     * guardada en el evento.
     */
    @Transactional
    public void onPagoConfirmado(PagoConfirmado aviso) {
        if (aviso == null || aviso.debtId() == null) {
            return;
        }
        Debt deuda = debts.findById(aviso.debtId()).orElse(null);
        if (deuda == null) {
            log.warn("Aviso de pago para una deuda que no existe: {}", aviso.debtId());
            return;
        }

        String referencia = aviso.gateway() + ":" + aviso.gatewayTxnId();
        boolean yaAplicado = events.findByDebtOrderByOccurredAtAsc(deuda).stream()
                .anyMatch(e -> e.getType() == DebtEvent.Type.payment_applied
                        && referencia.equals(e.getReference()));
        if (yaAplicado) {
            log.info("Aviso repetido del pago {}: no se abona de nuevo", aviso.paymentId());
            return;
        }

        BigDecimal porAplicar = aviso.amount() == null ? BigDecimal.ZERO : aviso.amount();

        if (aviso.installmentId() != null) {
            installments.findById(aviso.installmentId())
                    .filter(c -> c.getDebt().getId().equals(deuda.getId()))
                    .filter(c -> c.getStatus() == Installment.Status.pending)
                    .ifPresent(cuota -> marcarPagada(cuota, aviso.paidAt()));
        } else {
            //  Sin cuota indicada, se imputa de la mas antigua a la mas nueva,
            //  como cualquier abono en una cuenta corriente.
            List<Installment> pendientes = installments
                    .findByDebtAndStatus(deuda, Installment.Status.pending).stream()
                    .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate())).toList();
            for (Installment cuota : pendientes) {
                if (porAplicar.compareTo(cuota.getAmount()) < 0) {
                    break;
                }
                marcarPagada(cuota, aviso.paidAt());
                porAplicar = porAplicar.subtract(cuota.getAmount());
            }
        }

        Debt.Currency moneda = Debt.Currency.valueOf(aviso.currency());
        Instant pagadoEn = aviso.paidAt() == null ? Instant.now() : aviso.paidAt();
        events.save(DebtEvent.de(deuda, DebtEvent.Type.payment_applied, DebtEvent.Actor.system)
                .conMonto(aviso.amount(), moneda)
                .conReferencia(referencia));
        eventos.publicar(deuda, EventosService.PAGO_CONFIRMADO, datosDelPago(aviso, moneda, pagadoEn), pagadoEn);

        if (saldo(deuda).compareTo(BigDecimal.ZERO) == 0) {
            deuda.setStatus(Debt.Status.paid);
            events.save(DebtEvent.de(deuda, DebtEvent.Type.settled, DebtEvent.Actor.system));
            eventos.publicar(deuda, EventosService.DEUDA_SALDADA,
                    Map.of("saldada_en", EventosService.enChile(pagadoEn)), pagadoEn);
        }
        deuda.setUpdatedAt(Instant.now());
        debts.save(deuda);
    }

    /**
     * Lo que el acreedor necesita para imputar el pago en su propio sistema.
     * En UF va el valor usado: la UF cambia todos los dias, y sin ese dato
     * nadie podria reconstruir por que UF 38,5 fueron esos pesos.
     */
    private static Map<String, Object> datosDelPago(PagoConfirmado aviso, Debt.Currency moneda, Instant pagadoEn) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("pago_id", String.valueOf(aviso.paymentId()));
        datos.put("monto", EventosService.monto(aviso.amount(), moneda));
        datos.put("moneda", moneda.name());
        datos.put("monto_clp", aviso.amountClp());
        if (moneda == Debt.Currency.UF) {
            datos.put("valor_uf", aviso.ufValue());
        }
        datos.put("medio", aviso.gateway() == null ? null : aviso.gateway().toLowerCase());
        datos.put("pagado_en", EventosService.enChile(pagadoEn));
        return datos;
    }

    private void marcarPagada(Installment cuota, Instant cuando) {
        cuota.setStatus(Installment.Status.paid);
        cuota.setPaidAt(cuando == null ? Instant.now() : cuando);
        installments.save(cuota);
    }

    // ------------------------------------------------------------------
    //  Representacion
    // ------------------------------------------------------------------

    public Map<String, Object> toSummary(Debt deuda) {
        BigDecimal saldo = saldo(deuda);
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", deuda.getId());
        mapa.put("externalId", deuda.getExternalId());
        mapa.put("acreedor", deuda.getCreditor().getTradeName());
        mapa.put("acreedorRut", deuda.getCreditor().getRut());
        mapa.put("deudor", deuda.getDebtor().getFullName());
        mapa.put("deudorRut", deuda.getDebtor().getRut());
        mapa.put("concepto", deuda.getConcept());
        mapa.put("moneda", deuda.getCurrency());
        mapa.put("montoOriginal", deuda.getOriginalAmount());
        mapa.put("saldo", saldo);
        mapa.put("pagado", deuda.getOriginalAmount().subtract(saldo));
        mapa.put("estado", deuda.getStatus());
        mapa.put("actualizada", deuda.getUpdatedAt());
        return mapa;
    }

    private Map<String, Object> cargoPublico(DebtCharge cargo) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("concepto", cargo.getConcept());
        mapa.put("periodo", cargo.getPeriod());
        mapa.put("monto", cargo.getAmount());
        mapa.put("vencimiento", cargo.getDueDate());
        return mapa;
    }

    private Map<String, Object> cuotaPublica(Installment cuota) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", cuota.getId());
        mapa.put("numero", cuota.getNumber());
        mapa.put("vencimiento", cuota.getDueDate());
        mapa.put("monto", cuota.getAmount());
        mapa.put("estado", cuota.getStatus() == Installment.Status.void_ ? "anulada" : cuota.getStatus());
        mapa.put("pagadaEn", cuota.getPaidAt());
        return mapa;
    }

    private Map<String, Object> eventoPublico(DebtEvent evento) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("tipo", evento.getType());
        mapa.put("quien", evento.getActor());
        mapa.put("monto", evento.getAmount());
        mapa.put("moneda", evento.getCurrency());
        mapa.put("referencia", evento.getReference());
        mapa.put("ocurrioEn", evento.getOccurredAt());
        return mapa;
    }
}
