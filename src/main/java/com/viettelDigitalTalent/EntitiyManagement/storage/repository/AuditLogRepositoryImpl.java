package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void saveRawLog(String eventId, String source, String category, LocalDateTime timestamp,
                           Map<String, Object> rawData, String rawEvent) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .set("source", source)
                .set("category", category)
                .set("timestamp", timestamp)
                .set("rawData", rawData)
                .set("rawEvent", rawEvent)
                .setOnInsert("isEnriched", false);

        mongoTemplate.upsert(query, update, AuditLog.class);
    }

    @Override
    public void updateEnrichment(String eventId, Map<String, Object> enrichment) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .set("enrichment", enrichment)
                .set("isEnriched", true);

        // updateFirst (không upsert) — doc phải tồn tại trước (saveRawLog luôn chạy trước)
        mongoTemplate.updateFirst(query, update, AuditLog.class);
    }

    @Override
    public Map<String, Object> findEnrichmentByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) return Collections.emptyMap();
        AuditLog log = mongoTemplate.findById(eventId, AuditLog.class);
        return (log != null && log.getEnrichment() != null) ? log.getEnrichment() : Collections.emptyMap();
    }
}
