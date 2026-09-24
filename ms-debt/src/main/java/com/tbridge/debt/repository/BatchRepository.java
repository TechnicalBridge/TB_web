package com.tbridge.debt.repository;

import com.tbridge.debt.model.Batch;
import com.tbridge.debt.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    /** La idempotencia es por emisor: el mismo numero de lote de otro no choca. */
    Optional<Batch> findBySenderAndExternalId(Organization sender, String externalId);
}
