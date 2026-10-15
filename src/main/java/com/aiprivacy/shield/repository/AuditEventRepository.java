package com.aiprivacy.shield.repository;

import com.aiprivacy.shield.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop100ByOrderByIdDesc();

    long countByAction(String action);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.riskLevel IN ('HIGH', 'CRITICAL')")
    long countHighRiskEvents();
}
