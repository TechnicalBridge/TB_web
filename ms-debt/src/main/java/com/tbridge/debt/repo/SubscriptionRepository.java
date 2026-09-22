package com.tbridge.debt.repo;

import com.tbridge.debt.domain.Organization;
import com.tbridge.debt.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByOrganizationAndActiveTrue(Organization organization);

    Optional<Subscription> findByOrganizationAndUrl(Organization organization, String url);
}
