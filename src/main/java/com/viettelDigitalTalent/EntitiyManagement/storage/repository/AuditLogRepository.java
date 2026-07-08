package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditLogRepository {
    void saveRawLog(String eventId, String tenantId, String source, String category, LocalDateTime timestamp, Map<String, Object> rawData, String rawEvent);
    void updateEnrichment(String eventId, Map<String, Object> enrichment);
    Map<String, Object> findEnrichmentByEventId(String eventId);
    AuditLog findByEventId(String eventId);
    List<AuditLog> findRecentEvents(LocalDateTime since);
    List<AuditLog> findRecentEvents(LocalDateTime since, String tenantId);
    Page<AuditLog> findAlerts(Pageable pageable, String tenantId);
    long countByCategory(String category, String tenantId);
}
