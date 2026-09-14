package com.tbridge.auth.service;

import com.tbridge.auth.domain.MagicLink;
import com.tbridge.auth.domain.UserAccount;
import com.tbridge.auth.repo.MagicLinkRepository;
import com.tbridge.auth.repo.UserRepository;
import com.tbridge.common.jwt.JwtService;
import com.tbridge.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final Map<String, UserAccount> userStore = new ConcurrentHashMap<>();
    private final Map<String, MagicLink> linkStore = new ConcurrentHashMap<>();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        MagicLinkRepository links = mock(MagicLinkRepository.class);
        MailService mail = mock(MailService.class);
        JwtService jwt = new JwtService("unit-test-secret-key-32-chars!!");

        when(users.findByEmail(any())).thenAnswer(inv -> Optional.ofNullable(
                userStore.values().stream()
                        .filter(u -> u.getEmail().equals(inv.getArgument(0)))
                        .findFirst()
                        .orElse(null)
        ));
        when(users.findById(any())).thenAnswer(inv -> Optional.ofNullable(userStore.get(inv.getArgument(0))));
        when(users.existsByEmail(any())).thenAnswer(inv ->
                userStore.values().stream().anyMatch(u -> u.getEmail().equals(inv.getArgument(0))));
        when(users.save(any())).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            userStore.put(u.getId(), u);
            return u;
        });
        when(links.findById(any())).thenAnswer(inv -> Optional.ofNullable(linkStore.get(inv.getArgument(0))));
        when(links.save(any())).thenAnswer(inv -> {
            MagicLink l = inv.getArgument(0);
            linkStore.put(l.getId(), l);
            return l;
        });

        authService = new AuthService(users, links, jwt, mail, "http://localhost:5173", 15);
    }

    @Test
    void magicLinkIsSingleUse() {
        Map<String, Object> sent = authService.requestMagicLink("ana.perez@correo.com", "Ana Pérez");
        String token = (String) sent.get("token");
        assertNotNull(token);
        Map<String, Object> first = authService.verify(token, "Ana Pérez");
        assertNotNull(first.get("token"));
        ApiException ex = assertThrows(ApiException.class, () -> authService.verify(token, "Ana Pérez"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertTrue(ex.getMessage().toLowerCase().contains("utilizado"));
    }

    @Test
    void expiredLinkIsRejected() {
        MagicLink link = new MagicLink();
        link.setId("expired-uuid");
        link.setEmail("ana.perez@correo.com");
        link.setCreatedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        link.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        linkStore.put(link.getId(), link);
        ApiException ex = assertThrows(ApiException.class, () -> authService.verify("expired-uuid", null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
