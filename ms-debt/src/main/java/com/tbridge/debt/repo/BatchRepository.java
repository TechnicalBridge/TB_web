package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Batch;
import com.tbridge.debt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    /** La idempotencia es por emisor: el mismo numero de lote de otro no choca. */
    Optional<Batch> findBySenderAndExternalId(Organization sender, String externalId);

    List<Batch> findByCreditorOrderByCutOffDesc(Organization creditor);
}
