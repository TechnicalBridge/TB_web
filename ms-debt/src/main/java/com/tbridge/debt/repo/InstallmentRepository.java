package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByDebtOrderByNumberAsc(Debt debt);

    List<Installment> findByDebtAndStatus(Debt debt, Installment.Status status);
}
