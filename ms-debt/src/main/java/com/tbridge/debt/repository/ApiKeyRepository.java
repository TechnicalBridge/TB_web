package com.tbridge.debt.repository;

import com.tbridge.debt.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
