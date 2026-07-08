package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldOccurrence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class UnknownFieldOccurrenceRepositoryImpl implements UnknownFieldOccurrenceRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void record(String tenantId, String eventType, String eventId, String fieldName, String sampleValue, LocalDateTime occurredAt) {
        if (eventType == null || fieldName == null || occurredAt == null) return;
        mongoTemplate.insert(new UnknownFieldOccurrence(
                null,
                tenantId,
                eventType,
                fieldName,
                eventId,
                sampleValue,
                occurredAt
        ));
    }

    @Override
    public List<UnknownFieldOccurrence> findAll(String tenantId) {
        Query query = new Query(tenantCriteria(tenantId))
                .with(Sort.by(Sort.Direction.ASC, "occurredAt"));
        return mongoTemplate.find(query, UnknownFieldOccurrence.class);
    }

    @Override
    public List<UnknownFieldOccurrence> findPage(String tenantId, String fieldName, LocalDateTime from, int page, int size) {
        Query query = new Query(tenantCriteria(tenantId));
        if (from != null) {
            query.addCriteria(Criteria.where("occurredAt").gte(from));
        }
        if (fieldName != null && !fieldName.isBlank()) {
            query.addCriteria(Criteria.where("fieldName").regex(Pattern.compile(Pattern.quote(fieldName), Pattern.CASE_INSENSITIVE)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "occurredAt"));
        query.skip((long) page * size).limit(size);
        return mongoTemplate.find(query, UnknownFieldOccurrence.class);
    }

    @Override
    public long count(String tenantId, String fieldName, LocalDateTime from) {
        Query query = new Query(tenantCriteria(tenantId));
        if (from != null) {
            query.addCriteria(Criteria.where("occurredAt").gte(from));
        }
        if (fieldName != null && !fieldName.isBlank()) {
            query.addCriteria(Criteria.where("fieldName").regex(Pattern.compile(Pattern.quote(fieldName), Pattern.CASE_INSENSITIVE)));
        }
        return mongoTemplate.count(query, UnknownFieldOccurrence.class);
    }

    private Criteria tenantCriteria(String tenantId) {
        return Criteria.where("tenantId").is(tenantId == null || tenantId.isBlank() ? "default" : tenantId);
    }
}
