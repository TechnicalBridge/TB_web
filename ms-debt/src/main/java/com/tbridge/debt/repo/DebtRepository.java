package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Debt;
import com.tbridge.debt.domain.Debtor;
import com.tbridge.debt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    /**
     * Lo que ve un acreedor: SOLO lo suyo.
     *
     * El modelo anterior resolvia esto con findAll() y por eso cualquier
     * acreedor veia las deudas de todos.
     */
    List<Debt> findByCreditorOrderByUpdatedAtDesc(Organization creditor);

    List<Debt> findByDebtorOrderByUpdatedAtDesc(Debtor debtor);

    Optional<Debt> findByCreditorAndExternalId(Organization creditor, String externalId);

    long countByCreditorAndStatus(Organization creditor, Debt.Status status);
}
