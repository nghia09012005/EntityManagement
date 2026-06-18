package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import java.time.LocalDateTime;
import java.util.Map;

public interface AuditLogRepository {
    void saveRawLog(String eventId, String source, String category, LocalDateTime timestamp, Map<String, Object> rawData);
    void updateEnrichment(String eventId, Map<String, Object> enrichment);
}
