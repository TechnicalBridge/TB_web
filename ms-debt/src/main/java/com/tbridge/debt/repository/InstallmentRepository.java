package com.tbridge.debt.repository;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByDebtOrderByNumberAsc(Debt debt);

    List<Installment> findByDebtAndStatus(Debt debt, Installment.Status status);
}
