package com.tbridge.payments.repo;

import com.tbridge.payments.domain.DebtNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DebtNotificationRepository extends JpaRepository<DebtNotification, Long> {

    /** Lo pendiente que ya toca reintentar. Por aca entra el despachador. */
    List<DebtNotification> findByStatusAndNextAttemptAtBefore(
            DebtNotification.Status status, Instant limite);

    Optional<DebtNotification> findByPaymentId(Long paymentId);
}
