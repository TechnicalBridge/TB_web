package com.tbridge.payments.repo;

import com.tbridge.payments.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Lo que ve un deudor: lo suyo, por RUT. */
    List<Payment> findByDebtorRutOrderByCreatedAtDesc(String debtorRut);

    /**
     * Lo que ve un acreedor: SOLO lo suyo.
     *
     * El modelo anterior devolvia findAll() para cualquier acreedor, asi que
     * todos veian los pagos de todos. No era un descuido del codigo: no habia
     * columna por la que filtrar.
     */
    List<Payment> findByCreditorRutOrderByCreatedAtDesc(String creditorRut);

    /** Para reconocer un webhook que ya se proceso. */
    Optional<Payment> findByGatewayAndGatewayTxnId(Payment.Gateway gateway, String gatewayTxnId);

    List<Payment> findByDebtIdOrderByCreatedAtDesc(Long debtId);
}
