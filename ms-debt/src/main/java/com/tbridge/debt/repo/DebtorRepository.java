package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debtor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtorRepository extends JpaRepository<Debtor, Long> {

    Optional<Debtor> findByRut(String rut);

    /** Solo para la transicion, mientras ms-auth siga emitiendo tokens sin RUT. */
    Optional<Debtor> findFirstByEmailIgnoreCase(String email);
}
