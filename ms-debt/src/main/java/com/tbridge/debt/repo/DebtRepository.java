package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, String> {

    List<Debt> findByDebtorEmailOrderByCreatedAtDesc(String email);

    List<Debt> findAllByOrderByCreatedAtDesc();
}
