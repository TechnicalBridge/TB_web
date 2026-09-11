package com.digitalbot.config;

import com.digitalbot.domain.Payment;
import com.digitalbot.domain.UserAccount;
import com.digitalbot.repo.PaymentRepository;
import com.digitalbot.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PaymentRepository payments;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository users, PaymentRepository payments, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.payments = payments;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        String demoId = "user-demo";
        UserAccount demo = new UserAccount();
        demo.setId(demoId);
        demo.setName("Cuenta Demo");
        demo.setEmail("demo@digitalbot.com");
        demo.setPasswordHash(passwordEncoder.encode("demo1234"));
        demo.setRole("user");
        demo.setPlanId("profesional");
        demo.setCreatedAt(Instant.parse("2026-06-01T10:00:00.000Z"));
        users.save(demo);

        payments.save(payment("pay-1", demoId, "profesional", 49, "tarjeta", "completado",
                "2026-08-12T10:00:00.000Z", "4242", "DBP-88421"));
        payments.save(payment("pay-2", demoId, "profesional", 49, "transferencia", "completado",
                "2026-07-12T10:00:00.000Z", null, "DBP-77103"));
        payments.save(payment("pay-3", demoId, "esencial", 19, "billetera", "pendiente",
                "2026-09-01T10:00:00.000Z", null, "DBP-90311"));
    }

    private Payment payment(
            String id,
            String userId,
            String planId,
            int amount,
            String method,
            String status,
            String createdAt,
            String last4,
            String reference
    ) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setUserId(userId);
        payment.setPlanId(planId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setMethod(method);
        payment.setStatus(status);
        payment.setCreatedAt(Instant.parse(createdAt));
        payment.setLast4(last4);
        payment.setReference(reference);
        return payment;
    }
}
