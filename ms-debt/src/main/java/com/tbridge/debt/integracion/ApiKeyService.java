package com.tbridge.debt.integracion;

import com.tbridge.debt.domain.ApiKey;
import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.repo.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Las credenciales de maquina.
 *
 * <p>La clave se muestra una sola vez, al emitirla. En la base queda su huella
 * SHA-256, asi que ni DataBridge puede recordarsela a nadie: si se pierde, se
 * emite otra y se revoca la anterior.
 */
@Service
public class ApiKeyService {

    private static final SecureRandom AZAR = new SecureRandom();

    private final ApiKeyRepository claves;

    public ApiKeyService(ApiKeyRepository claves) {
        this.claves = claves;
    }

    public record Emitida(String clave, ApiKey registro) {}

    @Transactional
    public Emitida emitir(Organization organizacion, String nombre) {
        byte[] azar = new byte[32];
        AZAR.nextBytes(azar);
        String clave = "tbk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(azar);

        ApiKey registro = new ApiKey();
        registro.setOrganization(organizacion);
        registro.setName(nombre);
        registro.setKeyHash(huella(clave));
        registro.setPrefix(clave.substring(0, 12));
        claves.save(registro);
        return new Emitida(clave, registro);
    }

    /** La organizacion dueña de la clave, o vacio si no sirve. */
    @Transactional
    public Optional<Organization> autenticar(String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        return claves.findByKeyHashAndRevokedAtIsNull(huella(clave)).map(registro -> {
            registro.setLastUsedAt(Instant.now());
            claves.save(registro);
            return registro.getOrganization();
        });
    }

    public static String huella(String clave) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clave.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la huella", e);
        }
    }
}
