package com.tbridge.payments.service;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.payments.domain.DebtNotification;
import com.tbridge.payments.domain.Payment;
import com.tbridge.payments.domain.PaymentEvent;
import com.tbridge.payments.domain.UfValue;
import com.tbridge.payments.repo.DebtNotificationRepository;
import com.tbridge.payments.repo.PaymentEventRepository;
import com.tbridge.payments.repo.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * El cobro.
 *
 * <p>Tres reglas gobiernan este archivo:
 *
 * <ol>
 *   <li><b>El monto no lo decide el cliente.</b> Sale de ms-debt, que es quien
 *       manda sobre la deuda.</li>
 *   <li><b>Nada se sobreescribe.</b> Cada transicion queda como una fila nueva
 *       en el libro; el estado del pago es solo la proyeccion del ultimo.</li>
 *   <li><b>El aviso a ms-debt no se manda aqui.</b> Se deja encolado en la
 *       misma transaccion y sale despues, con reintentos.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentEventRepository eventos;
    private final DebtNotificationRepository avisos;
    private final WebhookVerifier verifier;
    private final DebtGateway deudas;
    private final UfService uf;
    private final String publicUrl;

    public PaymentService(
            PaymentRepository payments,
            PaymentEventRepository eventos,
            DebtNotificationRepository avisos,
            WebhookVerifier verifier,
            DebtGateway deudas,
            UfService uf,
            @Value("${app.public-url}") String publicUrl
    ) {
        this.payments = payments;
        this.eventos = eventos;
        this.avisos = avisos;
        this.verifier = verifier;
        this.deudas = deudas;
        this.uf = uf;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    // ------------------------------------------------------------------
    //  Iniciar el pago
    // ------------------------------------------------------------------

    @Transactional
    public Map<String, Object> checkout(JwtPrincipal user, Map<String, Object> body) {
        if (user != null && user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El acreedor no paga deudas");
        }
        Long debtId = numero(body.get("debtId"));
        Long installmentId = numero(body.get("installmentId"));
        if (debtId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica que deuda quieres pagar");
        }
        Payment.Gateway gateway = pasarela(body.get("gateway"));

        //  El monto y a quien se le debe salen de ms-debt, no del cuerpo.
        var deuda = deudas.obtener(debtId, installmentId, user == null ? null : user.email());

        Payment pago = new Payment();
        pago.setDebtId(deuda.debtId());
        pago.setInstallmentId(deuda.installmentId());
        pago.setDebtorRut(deuda.debtorRut());
        pago.setCreditorRut(deuda.creditorRut());
        pago.setAmount(deuda.amount());
        pago.setCurrency(Payment.Currency.valueOf(deuda.currency()));
        pago.setGateway(gateway);
        pago.setStatus(Payment.Status.created);
        pago.setCreatedAt(Instant.now());
        payments.save(pago);
        eventos.save(PaymentEvent.de(pago.getId(), PaymentEvent.Type.created, PaymentEvent.Source.portal));

        Map<String, Object> respuesta = publico(pago);
        respuesta.put("checkoutUrl", enlaceDePago(pago));
        return respuesta;
    }

    private String enlaceDePago(Payment pago) {
        return publicUrl + "/pasarela/" + pago.getId() + "?sig=" + firma(pago);
    }

    /**
     * La firma del enlace de pago.
     *
     * No se guarda: se recalcula. Guardar una firma que se puede derivar es
     * una copia mas que puede quedar desincronizada.
     */
    private String firma(Payment pago) {
        return verifier.sign(
                String.valueOf(pago.getId()),
                pago.getAmount().toPlainString(),
                String.valueOf(pago.getDebtId())
        );
    }

    // ------------------------------------------------------------------
    //  Consultar
    // ------------------------------------------------------------------

    public Map<String, Object> get(JwtPrincipal user, Long id) {
        Payment pago = buscar(id);
        if (user != null && !user.isCreditor() && !mismo(user.email(), pago.getDebtorRut())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No puedes ver este pago");
        }
        return publico(pago);
    }

    public Map<String, Object> publicGet(Long id, String sig) {
        return publico(conFirmaValida(id, sig));
    }

    /**
     * Lo que ve cada quien.
     *
     * <p>El acreedor ve SOLO lo suyo. Antes esta consulta era findAll() para
     * cualquier acreedor: todos veian los pagos de todos.
     */
    public List<Map<String, Object>> list(JwtPrincipal user) {
        List<Payment> filas = user.isCreditor()
                ? payments.findByCreditorRutOrderByCreatedAtDesc(user.email())
                : payments.findByDebtorRutOrderByCreatedAtDesc(user.email());
        return filas.stream().map(this::publico).toList();
    }

    /** El libro de un pago, para el panel y para auditar. */
    public List<Map<String, Object>> historia(JwtPrincipal user, Long id) {
        get(user, id);
        return eventos.findByPaymentIdOrderByIdAsc(id).stream().map(evento -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("tipo", evento.getType());
            fila.put("origen", evento.getSource());
            fila.put("firmaValida", evento.getSignatureOk());
            fila.put("ocurrioEn", evento.getOccurredAt());
            return fila;
        }).toList();
    }

    // ------------------------------------------------------------------
    //  Confirmar
    // ------------------------------------------------------------------

    @Transactional
    public Map<String, Object> confirmPublic(Long id, String sig) {
        Payment pago = conFirmaValida(id, sig);
        return confirmar(pago, PaymentEvent.Source.portal, null, null);
    }

    /**
     * El aviso de la pasarela.
     *
     * <p>Un webhook se reintenta: el mismo aviso puede llegar dos o tres
     * veces. La segunda no vuelve a cobrar, y la base lo garantiza con el
     * unico (gateway, gateway_txn_id) por si el codigo se equivoca.
     */
    @Transactional
    public Map<String, Object> webhook(String gateway, Map<String, Object> body, String signatureHeader) {
        Long id = numero(body.get("paymentId"));
        if (id == null) {
            id = numero(body.get("id"));
        }
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El aviso no dice que pago es");
        }
        Payment pago = buscar(id);

        String sig = signatureHeader == null || signatureHeader.isBlank()
                ? texto(body.get("signature")) : signatureHeader;
        boolean firmaOk = verifier.matches(
                sig, String.valueOf(pago.getId()),
                pago.getAmount().toPlainString(), String.valueOf(pago.getDebtId())
        );

        if (!firmaOk) {
            //  Un aviso con firma invalida no se aplica, pero se guarda:
            //  alguien mandando avisos falsos es algo que hay que poder ver.
            eventos.save(PaymentEvent
                    .de(pago.getId(), PaymentEvent.Type.failed, PaymentEvent.Source.webhook)
                    .conFirma(false));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma criptografica invalida");
        }

        return confirmar(pago, PaymentEvent.Source.webhook, texto(body.get("txnId")), true);
    }

    private Map<String, Object> confirmar(Payment pago, PaymentEvent.Source origen,
                                          String txnId, Boolean firmaOk) {
        //  Idempotencia: el pago ya cobrado se devuelve tal cual.
        if (pago.getStatus() == Payment.Status.paid) {
            return publico(pago);
        }

        if (pago.getCurrency() == Payment.Currency.UF) {
            UfValue valor = uf.delDia(LocalDate.now());
            pago.setUfValue(valor.getValue());
            pago.setAmountClp(uf.aPesos(pago.getAmount(), valor.getValue()));
        } else {
            pago.setAmountClp(pago.getAmount().longValue());
        }

        pago.setGatewayTxnId(txnId == null || txnId.isBlank()
                ? "int-" + pago.getId() : txnId);
        pago.setStatus(Payment.Status.paid);
        pago.setPaidAt(Instant.now());

        try {
            payments.save(pago);
            eventos.save(PaymentEvent.de(pago.getId(), PaymentEvent.Type.paid, origen)
                    .conFirma(firmaOk));
            //  El aviso queda encolado aqui, en la misma transaccion. Si
            //  ms-debt esta caido, el pago igual quedo guardado.
            if (avisos.findByPaymentId(pago.getId()).isEmpty()) {
                avisos.save(DebtNotification.para(pago.getId()));
            }
        } catch (DataIntegrityViolationException choque) {
            //  El unico de la pasarela salto: ese aviso ya se habia aplicado.
            throw new ApiException(HttpStatus.CONFLICT,
                    "Ese pago de la pasarela ya estaba registrado");
        }
        return publico(pago);
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private Payment buscar(Long id) {
        return payments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private Payment conFirmaValida(Long id, String sig) {
        Payment pago = buscar(id);
        if (!verifier.matches(sig, String.valueOf(pago.getId()),
                pago.getAmount().toPlainString(), String.valueOf(pago.getDebtId()))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma de checkout invalida");
        }
        return pago;
    }

    private Map<String, Object> publico(Payment pago) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", pago.getId());
        mapa.put("debtId", pago.getDebtId());
        mapa.put("installmentId", pago.getInstallmentId());
        mapa.put("amount", pago.getAmount());
        mapa.put("currency", pago.getCurrency());
        mapa.put("amountClp", pago.getAmountClp());
        mapa.put("ufValue", pago.getUfValue());
        mapa.put("gateway", pago.getGateway());
        mapa.put("status", pago.getStatus());
        mapa.put("paidAt", pago.getPaidAt());
        mapa.put("createdAt", pago.getCreatedAt());
        return mapa;
    }

    private static boolean mismo(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static Long numero(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            String limpio = String.valueOf(valor).trim();
            return limpio.isEmpty() ? null : Long.valueOf(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Payment.Gateway pasarela(Object valor) {
        try {
            return Payment.Gateway.valueOf(texto(valor).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Pasarela no soportada (Mercado Pago, Khipu o Webpay)");
        }
    }
}
