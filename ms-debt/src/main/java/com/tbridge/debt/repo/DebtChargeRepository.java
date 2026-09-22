package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtChargeRepository extends JpaRepository<DebtCharge, Long> {

    List<DebtCharge> findByDebtOrderByDueDateAsc(Debt debt);

    void deleteByDebt(Debt debt);
}
