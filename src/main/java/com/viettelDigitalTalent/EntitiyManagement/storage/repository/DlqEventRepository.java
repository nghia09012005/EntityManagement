package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DlqEventRepository extends MongoRepository<DlqEvent, String> {

    long countBySourceTopic(String sourceTopic);
    org.springframework.data.domain.Page<DlqEvent> findAllByTenantIdOrderByFailedAtDesc(String tenantId, org.springframework.data.domain.Pageable pageable);
    long countBySourceTopicAndTenantId(String sourceTopic, String tenantId);
    long countByTenantId(String tenantId);
}
