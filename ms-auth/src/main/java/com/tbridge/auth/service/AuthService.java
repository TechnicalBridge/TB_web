package com.tbridge.auth.service;

import com.tbridge.auth.domain.MagicLink;
import com.tbridge.auth.domain.UserAccount;
import com.tbridge.auth.repo.MagicLinkRepository;
import com.tbridge.auth.repo.UserRepository;
import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final MagicLinkRepository magicLinks;
    private final JwtService jwtService;
    private final MailService mailService;
    private final String publicUrl;
    private final long magicTtlMinutes;

    public AuthService(
            UserRepository users,
            MagicLinkRepository magicLinks,
            JwtService jwtService,
            MailService mailService,
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.magic-ttl-minutes:15}") long magicTtlMinutes
    ) {
        this.users = users;
        this.magicLinks = magicLinks;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.publicUrl = publicUrl;
        this.magicTtlMinutes = magicTtlMinutes;
    }

    @Transactional
    public Map<String, Object> requestMagicLink(String emailRaw, String nameRaw) {
        String email = normalizeEmail(emailRaw);
        if (!EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo no válido");
        }
        UserAccount existing = users.findByEmail(email).orElse(null);
        MagicLink link = new MagicLink();
        link.setId(UUID.randomUUID().toString());
        link.setEmail(email);
        link.setUserId(existing != null ? existing.getId() : null);
        Instant now = Instant.now();
        link.setCreatedAt(now);
        link.setExpiresAt(now.plus(magicTtlMinutes, ChronoUnit.MINUTES));
        magicLinks.save(link);

        if (existing == null && nameRaw != null && nameRaw.trim().length() >= 2) {
            // name is applied when the UUID is consumed
            link.setUserId(null);
        }

        String url = publicUrl.replaceAll("/$", "") + "/magic?token=" + link.getId();
        mailService.sendMagicLink(email, url);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("message", "Si el correo existe en cartera o es nuevo, recibirás un enlace de acceso.");
        body.put("expiresInMinutes", magicTtlMinutes);
        body.put("magicUrl", url);
        body.put("token", link.getId());
        if (nameRaw != null && !nameRaw.isBlank()) {
            body.put("pendingName", nameRaw.trim());
        }
        return body;
    }

    @Transactional
    public Map<String, Object> verify(String token, String nameRaw) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Falta el token de acceso");
        }
        MagicLink link = magicLinks.findById(token.trim()).orElse(null);
        if (link == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Enlace inválido");
        }
        if (link.isUsed()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Este enlace ya fue utilizado");
        }
        if (Instant.now().isAfter(link.getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El enlace expiró. Solicita uno nuevo.");
        }
        link.setUsedAt(Instant.now());
        magicLinks.save(link);

        UserAccount user = users.findByEmail(link.getEmail()).orElse(null);
        if (user == null) {
            user = new UserAccount();
            user.setId(UUID.randomUUID().toString());
            user.setEmail(link.getEmail());
            user.setName(resolveName(nameRaw, link.getEmail()));
            user.setRole("DEBTOR");
            user.setCreatedAt(Instant.now());
            users.save(user);
        } else if (nameRaw != null && nameRaw.trim().length() >= 2 && user.getName().contains("@")) {
            user.setName(nameRaw.trim());
            users.save(user);
        }
        String jwt = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), user.getName());
        return Map.of("token", jwt, "user", publicUser(user));
    }

    public Map<String, Object> me(JwtPrincipal principal) {
        UserAccount user = users.findById(principal.id()).orElse(null);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        return Map.of("user", publicUser(user));
    }

    public List<Map<String, String>> demoAccounts() {
        return List.of(
                Map.of("email", "ana.perez@correo.com", "name", "Ana Pérez", "role", "DEBTOR", "portal", "Technical Bridge"),
                Map.of("email", "demo@technicalbridge.com", "name", "Demo Deudor", "role", "DEBTOR", "portal", "Technical Bridge"),
                Map.of("email", "carlos.soto@databridge.com", "name", "Carlos Soto", "role", "CREDITOR", "portal", "DataBridge")
        );
    }

    public static Map<String, Object> publicUser(UserAccount user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        return map;
    }

    private static String normalizeEmail(String emailRaw) {
        return emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveName(String nameRaw, String email) {
        if (nameRaw != null && nameRaw.trim().length() >= 2) {
            return nameRaw.trim();
        }
        String local = email.substring(0, email.indexOf('@')).replace('.', ' ');
        if (local.isBlank()) {
            return "Deudor";
        }
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
