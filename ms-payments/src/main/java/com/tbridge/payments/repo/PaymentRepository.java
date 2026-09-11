package com.tbridge.payments.repo;

import com.tbridge.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByDebtorEmailOrderByCreatedAtDesc(String email);

    List<Payment> findByDebtIdOrderByCreatedAtDesc(String debtId);
}
