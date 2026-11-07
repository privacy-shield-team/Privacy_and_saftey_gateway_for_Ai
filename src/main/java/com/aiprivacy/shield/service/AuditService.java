package com.aiprivacy.shield.service;

import com.aiprivacy.shield.entity.AuditEvent;
import com.aiprivacy.shield.model.EvaluationResult;
import com.aiprivacy.shield.model.SensitiveFinding;
import com.aiprivacy.shield.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditEventRepository auditRepo;

    public AuditService(AuditEventRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional
    public void recordEvent(String policyName, String action, EvaluationResult result,
                            String strategyApplied, String modelUsed, long latencyMs) {
        String categories = result.getFindings().stream()
                .map(f -> f.getCategory().name())
                .distinct()
                .collect(Collectors.joining(","));

        String injections = String.join(" | ", result.getInjectionFlags());

        AuditEvent event = new AuditEvent(
                policyName,
                action,
                result.getRiskLevel().name(),
                result.getFindings().size(),
                categories.isBlank() ? "NONE" : categories,
                injections.isBlank() ? "NONE" : injections,
                strategyApplied,
                modelUsed,
                latencyMs
        );

        auditRepo.save(event);
    }

    public List<AuditEvent> getRecentEvents() {
        return auditRepo.findTop100ByOrderByIdDesc();
    }

    public String generateCsvExport() {
        List<AuditEvent> events = auditRepo.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Timestamp,Policy,Action,RiskLevel,FindingsCount,DetectedCategories,ThreatFlags,Strategy,ModelUsed,LatencyMs\n");
        for (AuditEvent ev : events) {
            sb.append(ev.getId()).append(",")
              .append("\"").append(ev.getTimestamp() != null ? ev.getTimestamp().toString() : "").append("\",")
              .append("\"").append(escapeCsv(ev.getPolicyName())).append("\",")
              .append("\"").append(escapeCsv(ev.getAction())).append("\",")
              .append("\"").append(escapeCsv(ev.getRiskLevel())).append("\",")
              .append(ev.getDetectionCount()).append(",")
              .append("\"").append(escapeCsv(ev.getDetectedCategories())).append("\",")
              .append("\"").append(escapeCsv(ev.getInjectionFlags())).append("\",")
              .append("\"").append(escapeCsv(ev.getStrategyApplied())).append("\",")
              .append("\"").append(escapeCsv(ev.getModelUsed())).append("\",")
              .append(ev.getLatencyMs()).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    public Map<String, Object> getStatistics() {
        long total = auditRepo.count();
        long blocked = auditRepo.countByAction("BLOCKED");
        long sanitized = auditRepo.countByAction("SANITIZED");
        long passed = auditRepo.countByAction("PASSED");
        long highRisk = auditRepo.countHighRiskEvents();

        return Map.of(
                "totalRequests", total,
                "blockedRequests", blocked,
                "sanitizedRequests", sanitized,
                "passedRequests", passed,
                "highRiskRequests", highRisk
        );
    }
}
