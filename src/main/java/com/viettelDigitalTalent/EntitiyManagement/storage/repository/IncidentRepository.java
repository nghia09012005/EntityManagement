package com.viettelDigitalTalent.EntitiyManagement.storage.repository;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;

public interface IncidentRepository extends MongoRepository<Incident, String> {
    Page<Incident> findAllByOrderByDetectedAtDesc(Pageable pageable);
    boolean existsByPatternNameAndWindowStart(String patternName, LocalDateTime windowStart);
}