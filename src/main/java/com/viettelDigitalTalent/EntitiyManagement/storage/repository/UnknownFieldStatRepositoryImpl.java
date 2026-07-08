package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldStat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class UnknownFieldStatRepositoryImpl implements UnknownFieldStatRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    public void record(String eventType, Map<String, Object> unknownFields) {
        record(eventType, null, unknownFields);
    }

    @Override
    public void record(String eventType, String eventId, Map<String, Object> unknownFields) {
        if (eventType == null || unknownFields == null || unknownFields.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : unknownFields.entrySet()) {
            String fieldName = entry.getKey();
            String sample = truncate(String.valueOf(entry.getValue()), 200);

            Query query = new Query(Criteria.where("eventType").is(eventType).and("fieldName").is(fieldName));
            Update update = new Update()
                    .inc("count", 1)
                    .set("lastSeen", now)
                    .set("sampleValue", sample)
                    .set("lastEventId", eventId)
                    .setOnInsert("eventType", eventType)
                    .setOnInsert("fieldName", fieldName);

            mongoTemplate.upsert(query, update, UnknownFieldStat.class);
        }
    }

    @Override
    public List<UnknownFieldStat> findTopByEventType(String eventType, int limit) {
        Query query = new Query(Criteria.where("eventType").is(eventType))
                .with(Sort.by(Sort.Direction.DESC, "count"))
                .limit(limit);
        return mongoTemplate.find(query, UnknownFieldStat.class);
    }

    @Override
    public List<String> findDistinctEventTypes() {
        return mongoTemplate.query(UnknownFieldStat.class)
                .distinct("eventType")
                .as(String.class)
                .all();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
