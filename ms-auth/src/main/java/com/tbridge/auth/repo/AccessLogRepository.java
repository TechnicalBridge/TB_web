package com.tbridge.auth.repo;

import com.tbridge.auth.domain.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    /** Intentos recientes desde un mismo origen, para frenar fuerza bruta. */
    List<AccessLog> findByIpHashAndOccurredAtAfter(String ipHash, Instant desde);
}
