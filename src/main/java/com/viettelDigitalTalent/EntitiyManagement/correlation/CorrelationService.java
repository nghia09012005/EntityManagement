package com.viettelDigitalTalent.EntitiyManagement.correlation;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelationService {

    private final List<CorrelationRule> rules;
    private final IncidentRepository incidentRepository;

    public void evaluate(List<AuditLog> events, LocalDateTime windowStart, String tenantId) {
        if (events.isEmpty()) return;

        for (CorrelationRule rule : rules) {
            try {
                rule.evaluate(events, windowStart).ifPresent(incident -> {
                    incident.setId(UUID.randomUUID().toString());
                    incident.setTenantId(tenantId);
                    boolean exists = incidentRepository.existsByPatternNameAndWindowStartAndTenantId(
                            incident.getPatternName(), windowStart, tenantId);
                    if (!exists) {
                        incidentRepository.save(incident);
                        log.info("[Correlation] Incident mới: [{}] {}", incident.getSeverity(), incident.getTitle());
                    }
                });
            } catch (Exception e) {
                log.error("[Correlation] Rule {} lỗi: {}", rule.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}