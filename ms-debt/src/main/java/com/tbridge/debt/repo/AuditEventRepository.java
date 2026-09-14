package com.tbridge.debt.repo;

import com.tbridge.debt.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    List<AuditEvent> findByDebtIdOrderByAtAsc(String debtId);
}
