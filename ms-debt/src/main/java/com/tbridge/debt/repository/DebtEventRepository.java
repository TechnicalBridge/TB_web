package com.tbridge.debt.repository;

import com.tbridge.debt.model.Debt;
import com.tbridge.debt.model.DebtEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DebtEventRepository extends JpaRepository<DebtEvent, Long> {

    List<DebtEvent> findByDebtOrderByOccurredAtAsc(Debt debt);

    List<DebtEvent> findByDebtInAndTypeAndOccurredAtAfter(Collection<Debt> debts, DebtEvent.Type type, Instant desde);

    List<DebtEvent> findByDebtIn(Collection<Debt> debts);

    boolean existsByDebtAndTypeAndOccurredAtAfter(Debt debt, DebtEvent.Type type, Instant desde);
}
