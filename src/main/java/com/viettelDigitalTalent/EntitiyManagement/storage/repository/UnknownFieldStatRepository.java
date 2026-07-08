package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldStat;

import java.util.List;
import java.util.Map;

public interface UnknownFieldStatRepository {

    void record(String eventType, String eventId, Map<String, Object> unknownFields);

    List<UnknownFieldStat> findTopByEventType(String eventType, int limit);

    List<String> findDistinctEventTypes();
}
