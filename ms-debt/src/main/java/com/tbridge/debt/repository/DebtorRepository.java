package com.tbridge.debt.repository;

import com.tbridge.debt.model.Debtor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtorRepository extends JpaRepository<Debtor, Long> {

    Optional<Debtor> findByRut(String rut);
}
