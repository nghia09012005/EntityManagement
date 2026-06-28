package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditLogRepository {
    void saveRawLog(String eventId, String source, String category, LocalDateTime timestamp, Map<String, Object> rawData, String rawEvent);
    void updateEnrichment(String eventId, Map<String, Object> enrichment);
    Map<String, Object> findEnrichmentByEventId(String eventId);
    List<AuditLog> findRecentEvents(LocalDateTime since);
    Page<AuditLog> findAlerts(Pageable pageable);
    long countByCategory(String category);
}
