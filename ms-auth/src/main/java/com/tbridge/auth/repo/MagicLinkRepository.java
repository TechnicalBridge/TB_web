package com.tbridge.auth.repo;

import com.tbridge.auth.domain.MagicLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagicLinkRepository extends JpaRepository<MagicLink, Long> {

    Optional<MagicLink> findByTokenHash(String tokenHash);
}
