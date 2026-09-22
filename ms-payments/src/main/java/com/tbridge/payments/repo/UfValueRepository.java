package com.tbridge.payments.repo;

import com.tbridge.payments.domain.UfValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UfValueRepository extends JpaRepository<UfValue, LocalDate> {

    /** El valor mas reciente que no sea posterior al dia pedido. */
    Optional<UfValue> findFirstByDayLessThanEqualOrderByDayDesc(LocalDate day);
}
