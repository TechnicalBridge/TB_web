package com.tbridge.debt.service;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Debtor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hacerle llegar al deudor su codigo de acceso.
 *
 * <p>Es lo que hara el motor de campana cuando exista: la cartera llega, la
 * campana dice por que canales, y a cada deudor le llega su codigo. Mientras
 * tanto lo dispara a mano el personal del acreedor, deuda por deuda.
 *
 * <p><b>El codigo nunca vuelve a quien lo pidio.</b> ms-auth lo genera y lo
 * manda al correo del deudor; esta respuesta solo dice a donde se mando. Si el
 * personal pudiera verlo, podria entrar como el deudor.
 */
@Service
@Transactional(readOnly = true)
public class AccesoService {

    private final DebtService debts;
    private final RestClient rest = RestClient.create();
    private final String authUrl;
    private final String internalKey;

    public AccesoService(DebtService debts,
                         @Value("${app.auth-url:http://127.0.0.1:8081}") String authUrl,
                         @Value("${app.internal-key}") String internalKey) {
        this.debts = debts;
        this.authUrl = authUrl.replaceAll("/$", "");
        this.internalKey = internalKey;
    }

    public Map<String, Object> enviarCodigo(JwtPrincipal user, Long debtId) {
        if (user == null || !user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El codigo lo envia el acreedor, no el deudor");
        }
        Debt deuda = debts.requireVisible(user, debtId);
        if (deuda.getStatus() == Debt.Status.withdrawn || deuda.getStatus() == Debt.Status.paid) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa deuda ya no esta en cobranza");
        }
        Debtor deudor = deuda.getDebtor();
        if (deudor.getEmail() == null || deudor.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El deudor no tiene correo registrado, y el envio por WhatsApp aun no esta conectado");
        }

        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("rut", deudor.getRut());
        pedido.put("canales", List.of("correo"));
        pedido.put("correo", deudor.getEmail());
        pedido.put("acreedor", deuda.getCreditor().getTradeName());
        pedido.put("paraQue", deuda.getExternalId());

        Map<?, ?> emitido;
        try {
            emitido = rest.post()
                    .uri(authUrl + "/internal/codigos")
                    .header("X-Internal-Key", internalKey)
                    .body(pedido)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo emitir el codigo: el servicio de acceso no responde");
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("enviado", true);
        respuesta.put("canal", "correo");
        respuesta.put("destino", enmascarar(deudor.getEmail()));
        respuesta.put("expiraEn", emitido == null ? null : emitido.get("expiraEn"));
        return respuesta;
    }

    /** felipe.rojas@correo.cl -> fe**********@correo.cl: se reconoce sin exponerlo. */
    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 2) {
            return "***" + correo.substring(Math.max(arroba, 0));
        }
        return correo.substring(0, 2) + "*".repeat(arroba - 2) + correo.substring(arroba);
    }
}
