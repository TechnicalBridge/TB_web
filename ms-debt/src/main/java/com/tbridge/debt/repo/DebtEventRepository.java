package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.DebtEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtEventRepository extends JpaRepository<DebtEvent, Long> {

    List<DebtEvent> findByDebtOrderByOccurredAtAsc(Debt debt);
}
