package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Mandate;
import com.tbridge.debt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MandateRepository extends JpaRepository<Mandate, Long> {

    List<Mandate> findByAgencyAndCreditorAndStatus(
            Organization agency, Organization creditor, Mandate.Status status);

    List<Mandate> findByAgencyAndStatus(Organization agency, Mandate.Status status);
}
