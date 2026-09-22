package com.tbridge.auth.web;

import com.tbridge.auth.service.AuthService;
import com.tbridge.auth.service.MailService;
import com.tbridge.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Lo que el motor de campana le pide a ms-auth.
 *
 * <p>Emitir un codigo no lo hace el deudor: lo hace la campana, y despues lo
 * manda por WhatsApp y por correo. Por eso va detras de la clave interna y el
 * gateway no lo expone.
 */
@RestController
public class InternalController {

    private final AuthService auth;
    private final MailService correo;
    private final String internalKey;

    public InternalController(AuthService auth, MailService correo,
                              @Value("${app.internal-key}") String internalKey) {
        this.auth = auth;
        this.correo = correo;
        this.internalKey = internalKey;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/internal/codigos")
    public Map<String, Object> emitir(
            @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @RequestBody Map<String, Object> body
    ) {
        if (clave == null || !clave.equals(internalKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave interna invalida");
        }
        List<String> canales = body.get("canales") instanceof List<?> lista
                ? (List<String>) lista : List.of("correo");

        Map<String, Object> emitido = auth.emitirCodigo(
                String.valueOf(body.get("rut")), canales,
                body.get("paraQue") == null ? null : String.valueOf(body.get("paraQue")));

        //  El envio por correo sale de aqui; el de WhatsApp lo hara el motor
        //  de campana cuando exista, con el mismo codigo.
        if (canales.contains("correo") && body.get("correo") != null) {
            correo.enviarCodigo(String.valueOf(body.get("correo")),
                    String.valueOf(emitido.get("codigo")),
                    body.get("acreedor") == null ? null : String.valueOf(body.get("acreedor")));
        }
        return emitido;
    }
}
