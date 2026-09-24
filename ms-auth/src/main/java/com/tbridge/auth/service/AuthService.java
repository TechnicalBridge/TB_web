package com.tbridge.auth.service;

import com.tbridge.auth.domain.AccessCode;
import com.tbridge.auth.domain.AccessLog;
import com.tbridge.auth.domain.MagicLink;
import com.tbridge.auth.domain.Session;
import com.tbridge.auth.domain.StaffUser;
import com.tbridge.auth.repo.AccessCodeRepository;
import com.tbridge.auth.repo.AccessLogRepository;
import com.tbridge.auth.repo.MagicLinkRepository;
import com.tbridge.auth.repo.StaffUserRepository;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Como entra cada quien.
 *
 * <p><b>El deudor entra con un codigo y sin cuenta.</b> No hay registro, no
 * hay contrasena. El codigo le llega por WhatsApp y por su correo registrado,
 * y el entra escribiendo la direccion del portal por su cuenta.
 *
 * <p>Por que importa: un phisher necesita que hagas clic en SU enlace. Si el
 * mensaje te manda a un sitio que puedes escribir, buscar o verificar antes de
 * entrar, el atacante pierde el control del destino, que era todo lo que
 * tenia. Es la separacion entre <i>donde vas</i> y <i>quien te escribio</i>,
 * que es la definicion de verificacion fuera de banda.
 *
 * <p><b>El RUT tambien se pide.</b> No es friccion gratuita: el codigo es
 * corto para poder dictarlo, y sin un segundo dato, probar codigos al azar
 * seria barato.
 */
@Service
public class AuthService {

    /**
     * Alfabeto del codigo: sin 0/O ni 1/I/L.
     *
     * Un codigo se lee en un mensaje y a veces se dicta por telefono. Cada par
     * ambiguo es un deudor que escribe mal, falla y desconfia.
     */
    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LARGO_CODIGO = 6;

    private static final Pattern RUT = Pattern.compile("^\\d{7,8}-[\\dK]$");
    private static final SecureRandom AZAR = new SecureRandom();

    /** Mas de esto desde un mismo origen en diez minutos huele a fuerza bruta. */
    private static final int INTENTOS_POR_ORIGEN = 10;

    private final AccessCodeRepository codigos;
    private final MagicLinkRepository enlaces;
    private final StaffUserRepository personal;
    private final AccessLogRepository bitacora;
    private final JwtService jwt;
    private final SessionService sesiones;
    private final MailService correo;
    private final String publicUrl;
    private final long codeTtlHours;
    private final long magicTtlMinutes;

    /**
     * Una sesion recien abierta o renovada.
     *
     * <p>El JWT va en el cuerpo de la respuesta y la llave de renovacion en una
     * cookie: por eso viajan separados, y el controlador arma cada uno.
     */
    public record Sesion(String token, Map<String, Object> user, SessionService.Llave llave) {}

    public AuthService(
            AccessCodeRepository codigos,
            MagicLinkRepository enlaces,
            StaffUserRepository personal,
            AccessLogRepository bitacora,
            JwtService jwt,
            SessionService sesiones,
            MailService correo,
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.code-ttl-hours:24}") long codeTtlHours,
            @Value("${app.magic-ttl-minutes:15}") long magicTtlMinutes
    ) {
        this.codigos = codigos;
        this.enlaces = enlaces;
        this.personal = personal;
        this.bitacora = bitacora;
        this.jwt = jwt;
        this.sesiones = sesiones;
        this.correo = correo;
        this.publicUrl = publicUrl.replaceAll("/$", "");
        this.codeTtlHours = codeTtlHours;
        this.magicTtlMinutes = magicTtlMinutes;
    }

    // ------------------------------------------------------------------
    //  Emitir el codigo (lo pide el motor de campana, no el deudor)
    // ------------------------------------------------------------------

    /**
     * Emite un codigo para un RUT y lo devuelve UNA vez.
     *
     * <p>Quien llama es el motor de campana, que lo manda por los canales que
     * correspondan. Despues de esta respuesta el codigo no existe en ninguna
     * parte: en la base solo queda su huella.
     */
    @Transactional
    public Map<String, Object> emitirCodigo(String rutCrudo, List<String> canales, String paraQue) {
        String rut = normalizarRut(rutCrudo);
        String codigo = generarCodigo();

        AccessCode registro = new AccessCode();
        registro.setCodeHash(huella(rut, codigo));
        registro.setDebtorRut(rut);
        registro.setChannels(canalesComoJson(canales));
        registro.setExpiresAt(Instant.now().plus(codeTtlHours, ChronoUnit.HOURS));
        registro.setIssuedFor(paraQue);
        codigos.save(registro);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("rut", rut);
        cuerpo.put("canales", canales);
        cuerpo.put("expiraEn", registro.getExpiresAt());
        cuerpo.put("comoSeUsa", "Entra a " + publicUrl + " y escribe tu RUT y el codigo");
        return cuerpo;
    }

    // ------------------------------------------------------------------
    //  Entrar con el codigo
    // ------------------------------------------------------------------

    /**
     * Entrar con el codigo.
     *
     * <p><b>No es transaccional a proposito.</b> Un intento fallido tiene que
     * quedar contado, y si todo el metodo fuera una transaccion, la excepcion
     * que rechaza el intento se llevaria el contador con ella: probar codigos
     * al azar saldria gratis.
     */
    public Sesion entrarConCodigo(String rutCrudo, String codigoCrudo, String ip) {
        String ipHash = ip == null ? null : sha256(ip);
        frenarFuerzaBruta(ipHash);

        String rut = normalizarRut(rutCrudo);
        String codigo = (codigoCrudo == null ? "" : codigoCrudo)
                .trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");

        AccessCode registro = codigos
                .findFirstByDebtorRutAndConsumedAtIsNullOrderByIssuedAtDesc(rut)
                .orElse(null);

        //  El mismo mensaje para "no hay codigo" y para "no corresponde": si
        //  fueran distintos, se podria averiguar que RUT tienen deuda.
        if (registro == null) {
            anotar(rut, AccessLog.Method.code, AccessLog.Outcome.invalid, ipHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El codigo no corresponde a ese RUT");
        }
        if (registro.getAttempts() >= registro.getMaxAttempts()) {
            anotar(rut, AccessLog.Method.code, AccessLog.Outcome.exhausted, ipHash);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos con ese codigo. Pide uno nuevo.");
        }
        if (Instant.now().isAfter(registro.getExpiresAt())) {
            anotar(rut, AccessLog.Method.code, AccessLog.Outcome.expired, ipHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El codigo expiro. Pide uno nuevo.");
        }
        if (!igualesEnTiempoConstante(registro.getCodeHash(), huella(rut, codigo))) {
            registro.fallo();
            codigos.save(registro);
            anotar(rut, AccessLog.Method.code, AccessLog.Outcome.invalid, ipHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El codigo no corresponde a ese RUT");
        }

        //  De un solo uso: consumirlo es parte de entrar.
        registro.consumir();
        codigos.save(registro);
        anotar(rut, AccessLog.Method.code, AccessLog.Outcome.granted, ipHash);

        //  La sesion se abre en su propia transaccion (SessionService), porque
        //  este metodo no tiene una a proposito: ver el comentario de arriba.
        return sesionDeDeudor(rut, ipHash);
    }

    // ------------------------------------------------------------------
    //  El respaldo: enlace de un solo uso
    // ------------------------------------------------------------------

    /**
     * El camino de excepcion, para quien no logra entrar con el codigo.
     *
     * <p>Se conserva a sabiendas de que es el patron que el caso critica. Por
     * eso dura quince minutos y no veinticuatro horas.
     */
    @Transactional
    public Map<String, Object> pedirEnlace(String rutCrudo, String email) {
        String rut = rutCrudo == null || rutCrudo.isBlank() ? null : normalizarRut(rutCrudo);
        if (email == null || !email.contains("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo no valido");
        }
        String token = UUID.randomUUID().toString();

        MagicLink enlace = new MagicLink();
        enlace.setTokenHash(sha256(token));
        enlace.setDebtorRut(rut);
        enlace.setEmail(email.trim().toLowerCase(Locale.ROOT));
        enlace.setExpiresAt(Instant.now().plus(magicTtlMinutes, ChronoUnit.MINUTES));
        enlaces.save(enlace);

        String url = publicUrl + "/magic?token=" + token;
        correo.enviarEnlace(enlace.getEmail(), url, magicTtlMinutes);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", "Si ese correo esta en cartera, recibiras un enlace de acceso.");
        cuerpo.put("expiraEnMinutos", magicTtlMinutes);
        return cuerpo;
    }

    @Transactional
    public Sesion entrarConEnlace(String token, String ip) {
        String ipHash = ip == null ? null : sha256(ip);
        MagicLink enlace = enlaces.findByTokenHash(sha256(token == null ? "" : token.trim()))
                .orElse(null);
        if (enlace == null || !enlace.vigente()) {
            anotar(enlace == null ? null : enlace.getDebtorRut(),
                    AccessLog.Method.magic_link, AccessLog.Outcome.invalid, ipHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Enlace invalido o vencido");
        }
        enlace.setConsumedAt(Instant.now());
        enlaces.save(enlace);
        anotar(enlace.getDebtorRut(), AccessLog.Method.magic_link, AccessLog.Outcome.granted, ipHash);

        //  El personal de una empresa tambien entra por enlace.
        StaffUser staff = personal.findByEmailIgnoreCase(enlace.getEmail()).orElse(null);
        if (staff != null && staff.habilitado()) {
            staff.setLastLoginAt(Instant.now());
            personal.save(staff);
            return sesionDePersonal(staff, ipHash);
        }
        if (enlace.getDebtorRut() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Ese correo no tiene acceso");
        }
        return sesionDeDeudor(enlace.getDebtorRut(), ipHash);
    }

    // ------------------------------------------------------------------
    //  Sesiones
    // ------------------------------------------------------------------

    /**
     * Cambia la llave de renovacion por un JWT nuevo y la llave siguiente.
     *
     * <p>Al personal se lo vuelve a buscar en cada renovacion: si lo dieron de
     * baja, pierde el acceso en cuanto vence su JWT, aunque su llave siga
     * vigente. Al deudor no hay a quien darlo de baja: no tiene cuenta.
     */
    public Sesion renovar(String llave, String ip) {
        String ipHash = ip == null ? null : sha256(ip);
        SessionService.Renovada renovada = sesiones.renovar(llave, ipHash);

        if (renovada.role() == Session.Role.DEBTOR) {
            return new Sesion(jwtDeDeudor(renovada.rut()), usuarioDeudor(renovada.rut()), renovada.llave());
        }
        StaffUser staff = personal.findByEmailIgnoreCase(renovada.email()).orElse(null);
        if (staff == null || !staff.habilitado()) {
            sesiones.revocarFamilia(renovada.familia());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Ese acceso ya no esta habilitado");
        }
        return new Sesion(jwtDePersonal(staff), usuarioPersonal(staff), renovada.llave());
    }

    public void cerrar(String llave) {
        sesiones.cerrar(llave);
    }

    private Sesion sesionDeDeudor(String rut, String ipHash) {
        SessionService.Llave llave = sesiones.abrir(Session.Role.DEBTOR, rut, null, ipHash);
        return new Sesion(jwtDeDeudor(rut), usuarioDeudor(rut), llave);
    }

    private Sesion sesionDePersonal(StaffUser staff, String ipHash) {
        SessionService.Llave llave = sesiones.abrir(Session.Role.CREDITOR, null, staff.getEmail(), ipHash);
        return new Sesion(jwtDePersonal(staff), usuarioPersonal(staff), llave);
    }

    private String jwtDeDeudor(String rut) {
        return jwt.issue(rut, null, "DEBTOR", null, rut);
    }

    private String jwtDePersonal(StaffUser staff) {
        return jwt.issue(String.valueOf(staff.getId()), staff.getEmail(),
                "CREDITOR", staff.getFullName(), staff.getOrgRut());
    }

    private static Map<String, Object> usuarioDeudor(String rut) {
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("rut", rut);
        usuario.put("role", "DEBTOR");
        return usuario;
    }

    private static Map<String, Object> usuarioPersonal(StaffUser staff) {
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("id", staff.getId());
        usuario.put("nombre", staff.getFullName());
        usuario.put("correo", staff.getEmail());
        usuario.put("empresaRut", staff.getOrgRut());
        usuario.put("role", "CREDITOR");
        return usuario;
    }

    // ------------------------------------------------------------------
    //  Auxiliares
    // ------------------------------------------------------------------

    private void frenarFuerzaBruta(String ipHash) {
        if (ipHash == null) {
            return;
        }
        long recientes = bitacora
                .findByIpHashAndOccurredAtAfter(ipHash, Instant.now().minus(10, ChronoUnit.MINUTES))
                .stream()
                .filter(registro -> registro.getOutcome() != AccessLog.Outcome.granted)
                .count();
        if (recientes >= INTENTOS_POR_ORIGEN) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Espera unos minutos.");
        }
    }

    private void anotar(String rut, AccessLog.Method metodo, AccessLog.Outcome resultado, String ipHash) {
        bitacora.save(AccessLog.de(rut, metodo, resultado, ipHash));
    }

    static String normalizarRut(String crudo) {
        String limpio = (crudo == null ? "" : crudo).trim().toUpperCase(Locale.ROOT)
                .replace(".", "").replace(" ", "");
        if (!limpio.contains("-") && limpio.length() > 1) {
            limpio = limpio.substring(0, limpio.length() - 1) + "-"
                    + limpio.charAt(limpio.length() - 1);
        }
        if (!RUT.matcher(limpio).matches() || !digitoVerificadorCorrecto(limpio)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUT no valido");
        }
        return limpio;
    }

    /** Modulo 11: el mismo calculo que valida APOFYX al recibir la cartera. */
    static boolean digitoVerificadorCorrecto(String rut) {
        String[] partes = rut.split("-");
        int suma = 0;
        int factor = 2;
        for (int i = partes[0].length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(partes[0].charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - suma % 11;
        String esperado = resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
        return esperado.equals(partes[1]);
    }

    private static String generarCodigo() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /**
     * La huella incluye el RUT.
     *
     * Asi dos deudores pueden tener el mismo codigo sin chocar en el indice
     * unico, y un codigo filtrado no sirve para el RUT de otra persona.
     */
    static String huella(String rut, String codigo) {
        return sha256(rut + ":" + codigo);
    }

    /**
     * Comparar sin delatar cuanto coincide.
     *
     * Una comparacion normal se detiene en el primer caracter distinto, y ese
     * tiempo, medido muchas veces, revela el valor.
     */
    private static boolean igualesEnTiempoConstante(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diferencia = 0;
        for (int i = 0; i < a.length(); i++) {
            diferencia |= a.charAt(i) ^ b.charAt(i);
        }
        return diferencia == 0;
    }

    static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la huella", e);
        }
    }

    private static String canalesComoJson(List<String> canales) {
        if (canales == null || canales.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", canales) + "\"]";
    }
}
