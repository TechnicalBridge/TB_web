package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Repactation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepactationRepository extends JpaRepository<Repactation, Long> {

    List<Repactation> findByDebtAndSupersededAtIsNull(Debt debt);
}
