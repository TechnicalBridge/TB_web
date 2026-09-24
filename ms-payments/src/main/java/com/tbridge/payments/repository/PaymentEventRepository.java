package com.tbridge.payments.repository;

import com.tbridge.payments.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIdOrderByIdAsc(Long paymentId);
}
