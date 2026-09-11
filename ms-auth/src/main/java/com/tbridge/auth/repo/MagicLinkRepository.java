package com.tbridge.auth.repo;

import com.tbridge.auth.domain.MagicLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MagicLinkRepository extends JpaRepository<MagicLink, String> {
}
