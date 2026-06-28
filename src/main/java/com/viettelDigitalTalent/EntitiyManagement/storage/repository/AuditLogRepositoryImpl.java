package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void saveRawLog(String eventId, String tenantId, String source, String category, LocalDateTime timestamp,
                           Map<String, Object> rawData, String rawEvent) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        Update update = new Update()
                .set("tenantId", tenantId)
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

    @Override
    public List<AuditLog> findRecentEvents(LocalDateTime since) {
        Query query = new Query(Criteria.where("timestamp").gte(since))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, AuditLog.class);
    }

    @Override
    public List<AuditLog> findRecentEvents(LocalDateTime since, String tenantId) {
        Query query = new Query(Criteria.where("timestamp").gte(since).and("tenantId").is(tenantId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, AuditLog.class);
    }

    @Override
    public Page<AuditLog> findAlerts(Pageable pageable, String tenantId) {
        Criteria criteria = Criteria.where("tenantId").is(tenantId).andOperator(
                new Criteria().orOperator(
                        Criteria.where("category").is("THREAT"),
                        Criteria.where("category").is("SECURITY_FINDING")
                )
        );
        long total = mongoTemplate.count(new Query(criteria), AuditLog.class);
        Query query = new Query(criteria)
                .with(pageable)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        List<AuditLog> content = mongoTemplate.find(query, AuditLog.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long countByCategory(String category, String tenantId) {
        Criteria criteria = Criteria.where("tenantId").is(tenantId)
                .and("category").is(category);
        return mongoTemplate.count(new Query(criteria), AuditLog.class);
    }
}
