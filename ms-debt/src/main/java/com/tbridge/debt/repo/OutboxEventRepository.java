package com.tbridge.debt.repo;

import com.tbridge.debt.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** De a cien, los mas antiguos primero: un atraso largo no se despacha de un golpe. */
    List<OutboxEvent> findTop100ByStatusAndNextAttemptAtBeforeOrderByIdAsc(
            OutboxEvent.Status status, Instant ahora);
}
