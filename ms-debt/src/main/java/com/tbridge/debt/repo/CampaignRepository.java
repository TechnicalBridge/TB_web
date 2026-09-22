package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Campaign;
import com.tbridge.debt.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByAgencyAndExternalId(Organization agency, String externalId);
}
