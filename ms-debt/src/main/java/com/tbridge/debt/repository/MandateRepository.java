package com.tbridge.debt.repository;

import com.tbridge.debt.model.Mandate;
import com.tbridge.debt.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MandateRepository extends JpaRepository<Mandate, Long> {

    List<Mandate> findByAgencyAndCreditorAndStatus(
            Organization agency, Organization creditor, Mandate.Status status);

    List<Mandate> findByAgencyAndStatus(Organization agency, Mandate.Status status);
}
