package com.tbridge.payments.repo;

import com.tbridge.payments.domain.UfValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface UfValueRepository extends JpaRepository<UfValue, LocalDate> {
}
