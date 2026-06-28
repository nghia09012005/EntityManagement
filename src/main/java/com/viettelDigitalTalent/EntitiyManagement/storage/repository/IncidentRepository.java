package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IncidentRepository extends MongoRepository<Incident, String> {
    Page<Incident> findAllByTenantIdOrderByDetectedAtDesc(String tenantId, Pageable pageable);
    List<Incident> findAllByTenantId(String tenantId);
    boolean existsByPatternNameAndWindowStartAndTenantId(String patternName, LocalDateTime windowStart, String tenantId);
}