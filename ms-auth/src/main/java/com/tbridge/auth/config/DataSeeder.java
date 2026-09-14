package com.tbridge.auth.config;

import com.tbridge.auth.domain.UserAccount;
import com.tbridge.auth.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;

    public DataSeeder(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        seed("ana.perez@correo.com", "Ana Pérez", "DEBTOR");
        seed("demo@technicalbridge.com", "Demo Deudor", "DEBTOR");
        seed("carlos.soto@databridge.com", "Carlos Soto", "CREDITOR");
    }

    private void seed(String email, String name, String role) {
        if (users.existsByEmail(email)) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setName(name);
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        users.save(user);
    }
}
