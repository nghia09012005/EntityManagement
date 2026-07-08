package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogSearchController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/{eventId}")
    public ResponseEntity<Object> findByEventId(@PathVariable String eventId) {
        AuditLog log = auditLogRepository.findByEventId(eventId);
        if (log == null) {
            return ResponseEntity.notFound().build();
        }
//        return ResponseEntity.ok(Map.of(
//                "eventId", log.getEventId(),
//                "tenantId", log.getTenantId(),
//                "source", log.getSource(),
//                "category", log.getCategory(),
//                "timestamp", log.getTimestamp(),
//                "rawData", log.getRawData(),
//                "rawEvent", log.getRawEvent(),
//                "isEnriched", log.isEnriched(),
//                "enrichment", log.getEnrichment()
//        ));

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", log.getEventId());
        response.put("tenantId", log.getTenantId());
        response.put("source", log.getSource());
        response.put("category", log.getCategory());
        response.put("timestamp", log.getTimestamp());
        response.put("rawData", log.getRawData());
        response.put("rawEvent", log.getRawEvent());
        response.put("isEnriched", log.isEnriched());
        response.put("enrichment", log.getEnrichment());

        return ResponseEntity.ok(response);
    }
}