package com.tbridge.payments.repository;

import com.tbridge.payments.model.UfValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface UfValueRepository extends JpaRepository<UfValue, LocalDate> {
}
