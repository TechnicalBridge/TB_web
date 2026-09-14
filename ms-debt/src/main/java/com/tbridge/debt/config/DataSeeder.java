package com.tbridge.debt.config;

import com.tbridge.debt.repo.DebtRepository;
import com.tbridge.debt.service.DebtService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final DebtRepository debts;
    private final DebtService service;

    public DataSeeder(DebtRepository debts, DebtService service) {
        this.debts = debts;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        if (debts.count() > 0) {
            return;
        }
        service.createDebt(
                "ana.perez@correo.com",
                "Ana Pérez",
                "Banco Estado",
                new BigDecimal("450000"),
                LocalDate.now().plusDays(12),
                "Crédito de consumo 2024"
        );
        service.createDebt(
                "ana.perez@correo.com",
                "Ana Pérez",
                "Retail Financiero",
                new BigDecimal("180000"),
                LocalDate.now().plusDays(5),
                "Tarjeta de casa comercial"
        );
        service.createDebt(
                "demo@technicalbridge.com",
                "Demo Deudor",
                "Caja Los Andes",
                new BigDecimal("320000"),
                LocalDate.now().plusDays(20),
                "Crédito social"
        );
    }
}
