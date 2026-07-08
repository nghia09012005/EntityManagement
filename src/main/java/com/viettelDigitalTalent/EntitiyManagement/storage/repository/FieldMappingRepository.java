package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.FieldMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FieldMappingRepository extends MongoRepository<FieldMapping, String> {

    List<FieldMapping> findByEventTypeAndEnabledTrue(String eventType);

    List<FieldMapping> findByTenantIdAndEventTypeAndEnabledTrue(String tenantId, String eventType);

    List<FieldMapping> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
