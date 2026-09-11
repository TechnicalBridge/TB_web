package com.digitalbot.repo;

import com.digitalbot.domain.PayToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayTokenRepository extends JpaRepository<PayToken, String> {
    Optional<PayToken> findByCodeAndUserId(String code, String userId);
    List<PayToken> findByUserIdAndStatus(String userId, String status);
}
