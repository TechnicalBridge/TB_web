package com.digitalbot.service;

import com.digitalbot.domain.UserAccount;
import com.digitalbot.dto.UserMapper;
import com.digitalbot.repo.UserRepository;
import com.digitalbot.security.JwtService;
import com.digitalbot.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> register(String nameRaw, String emailRaw, String password) {
        String name = nameRaw == null ? "" : nameRaw.trim();
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        if (name.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica tu nombre");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo no válido");
        }
        if (password == null || password.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese correo ya está registrado");
        }
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID().toString());
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("user");
        user.setPlanId(null);
        user.setCreatedAt(Instant.now());
        users.save(user);
        return Map.of("token", jwtService.issue(user), "user", UserMapper.publicUser(user));
    }

    public Map<String, Object> login(String emailRaw, String password) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null || user.isGuest() || user.getPasswordHash() == null
                || !passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        return Map.of("token", jwtService.issue(user), "user", UserMapper.publicUser(user));
    }

    @Transactional
    public Map<String, Object> guest() {
        String id = UUID.randomUUID().toString();
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setName("Invitado");
        user.setEmail("invitado-" + id.substring(0, 8) + "@digitalbot.local");
        user.setPasswordHash(null);
        user.setRole("guest");
        user.setPlanId(null);
        user.setCreatedAt(Instant.now());
        users.save(user);
        return Map.of("token", jwtService.issue(user), "user", UserMapper.publicUser(user));
    }
}
