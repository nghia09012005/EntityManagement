package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrichment")
@RequiredArgsConstructor
public class EnrichmentController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/event/{eventId}")
    public ResponseEntity<Map<String, Object>> getByEventId(@PathVariable String eventId) {
        return ResponseEntity.ok(auditLogRepository.findEnrichmentByEventId(eventId));
    }
}
