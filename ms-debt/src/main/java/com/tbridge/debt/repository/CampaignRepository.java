package com.tbridge.debt.repository;

import com.tbridge.debt.model.Campaign;
import com.tbridge.debt.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByAgencyAndExternalId(Organization agency, String externalId);

    List<Campaign> findByAgencyAndCreditorOrderByStartsOnDesc(Organization agency, Organization creditor);

    List<Campaign> findByStatus(Campaign.Status status);
}
