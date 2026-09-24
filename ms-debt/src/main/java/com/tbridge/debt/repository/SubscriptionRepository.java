package com.tbridge.debt.repository;

import com.tbridge.debt.model.Organization;
import com.tbridge.debt.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByOrganizationAndActiveTrue(Organization organization);

    Optional<Subscription> findByOrganizationAndUrl(Organization organization, String url);
}
