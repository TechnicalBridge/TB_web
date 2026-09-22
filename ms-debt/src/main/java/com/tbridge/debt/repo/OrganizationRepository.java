package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByRut(String rut);
}
