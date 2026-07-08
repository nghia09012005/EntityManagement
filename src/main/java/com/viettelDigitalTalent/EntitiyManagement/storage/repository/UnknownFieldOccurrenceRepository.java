package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldOccurrence;

import java.time.LocalDateTime;
import java.util.List;

public interface UnknownFieldOccurrenceRepository {
    void record(String tenantId, String eventType, String eventId, String fieldName, String sampleValue, LocalDateTime occurredAt);
    List<UnknownFieldOccurrence> findAll(String tenantId);
    List<UnknownFieldOccurrence> findPage(String tenantId, String fieldName, LocalDateTime from, int page, int size);
    long count(String tenantId, String fieldName, LocalDateTime from);
}
