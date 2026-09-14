package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, String> {

    List<Installment> findByDebtIdOrderByNumberAsc(String debtId);

    List<Installment> findByDebtIdAndStatus(String debtId, String status);
}
