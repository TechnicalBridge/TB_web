package com.tbridge.auth.web;

import com.tbridge.auth.service.AuthService;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Las puertas del portal.
 *
 * <p>La de adelante es el codigo. El enlace queda como respaldo.
 *
 * <p><b>Dos piezas por sesion, y viajan separadas.</b> El JWT va en el cuerpo:
 * el portal lo guarda en memoria y lo manda en cada peticion. La llave de
 * renovacion va en una cookie que JavaScript no puede leer, limitada a
 * {@code /api/auth}, y solo sirve para pedir el JWT siguiente. Asi, un script
 * inyectado en la pagina puede usar la sesion mientras la pagina este abierta,
 * pero no puede llevarsela: lo unico que dura esta fuera de su alcance.
 */
@RestController
public class AuthController {

    static final String COOKIE = "tb_renovacion";

    private final AuthService auth;
    private final JwtService jwt;
    private final boolean cookieSegura;

    public AuthController(AuthService auth, JwtService jwt,
                          @Value("${app.cookie-secure:false}") boolean cookieSegura) {
        this.auth = auth;
        this.jwt = jwt;
        this.cookieSegura = cookieSegura;
    }

    /** Entrar con RUT y codigo. */
    @PostMapping("/api/auth/acceso")
    public ResponseEntity<Map<String, Object>> acceso(@RequestBody Map<String, String> body,
                                                      HttpServletRequest peticion) {
        return responder(auth.entrarConCodigo(body.get("rut"), body.get("codigo"), origen(peticion)));
    }

    /** Pedir el enlace de respaldo. */
    @PostMapping("/api/auth/enlace")
    public ResponseEntity<Map<String, Object>> enlace(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(auth.pedirEnlace(body.get("rut"), body.get("correo")));
    }

    /** Canjear el enlace por una sesion. */
    @PostMapping("/api/auth/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body,
                                                      HttpServletRequest peticion) {
        return responder(auth.entrarConEnlace(body.get("token"), origen(peticion)));
    }

    /**
     * Cambiar la llave de renovacion por un JWT nuevo.
     *
     * <p>Si la llave no sirve, ademas del error se borra la cookie: si no, el
     * navegador la seguiria mandando en cada recarga de la pagina.
     */
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @CookieValue(name = COOKIE, required = false) String llave, HttpServletRequest peticion) {
        if (llave == null || llave.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay una sesion que renovar"));
        }
        try {
            return responder(auth.renovar(llave, origen(peticion)));
        } catch (ApiException e) {
            ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(e.getStatus());
            //  409 es "otra pestana la acaba de renovar": la cookie ya tiene la
            //  llave nueva y hay que dejarla donde esta.
            if (e.getStatus() != HttpStatus.CONFLICT) {
                respuesta.header(HttpHeaders.SET_COOKIE, borrarCookie().toString());
            }
            return respuesta.body(Map.of("error", e.getMessage()));
        }
    }

    /** Cerrar sesion de verdad: revoca la familia en el servidor, no solo en el navegador. */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String llave) {
        if (llave != null && !llave.isBlank()) {
            auth.cerrar(llave);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, borrarCookie().toString()).build();
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal JwtPrincipal user) {
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("id", user.id());
        usuario.put("nombre", user.name());
        usuario.put("correo", user.email());
        usuario.put("rut", user.rut());
        usuario.put("role", user.role());
        return Map.of("user", usuario);
    }

    private ResponseEntity<Map<String, Object>> responder(AuthService.Sesion sesion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("token", sesion.token());
        cuerpo.put("user", sesion.user());
        //  Para que el portal sepa cuando le toca renovar, en vez de enterarse
        //  por un 401 en medio de algo.
        cuerpo.put("expiraEnSegundos", jwt.vida().toSeconds());

        Duration vida = Duration.between(Instant.now(), sesion.llave().venceEn());
        ResponseCookie cookie = cookie(sesion.llave().valor(), vida.isNegative() ? Duration.ZERO : vida);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(cuerpo);
    }

    /**
     * La cookie de la llave.
     *
     * <ul>
     *   <li>{@code HttpOnly}: ningun script de la pagina la puede leer.</li>
     *   <li>{@code SameSite=Strict}: otro sitio no puede hacer que el navegador
     *       la mande, asi que no puede renovar ni cerrar la sesion de nadie.</li>
     *   <li>{@code Path=/api/auth}: solo viaja a donde sirve. Los otros cuatro
     *       servicios nunca la ven.</li>
     *   <li>{@code Secure} segun {@code COOKIE_SECURE}: con HTTPS tiene que ir
     *       encendido. Esta apagado por omision porque todo este proyecto se
     *       sirve por HTTP, y una cookie Secure sobre HTTP el navegador la
     *       descarta sin avisar: la sesion se caeria cada quince minutos.</li>
     * </ul>
     */
    private ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(cookieSegura)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(vida)
                .build();
    }

    private ResponseCookie borrarCookie() {
        return cookie("", Duration.ZERO);
    }

    /**
     * De donde viene el intento.
     *
     * Se usa solo para contar intentos por origen; de la IP se guarda su
     * huella, nunca la IP.
     */
    private String origen(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }
}
