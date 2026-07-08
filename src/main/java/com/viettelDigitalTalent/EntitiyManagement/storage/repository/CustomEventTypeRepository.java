package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CustomEventTypeRepository extends MongoRepository<CustomEventType, String> {
    List<CustomEventType> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<CustomEventType> findByTenantIdAndEventType(String tenantId, String eventType);
    boolean existsByTenantIdAndEventType(String tenantId, String eventType);
}
