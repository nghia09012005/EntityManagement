package com.viettelDigitalTalent.EntitiyManagement.ingestion.controller;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class LogIngestionController {

    @Autowired
    private IngestionService ingestionService;

    @PostMapping
    public ResponseEntity<String> receiveLog(@RequestBody String rawData,
                                             Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        ingestionService.sendToQueue(rawData, tenantId);
        return ResponseEntity.accepted().body("Log queued");
    }

    @SuppressWarnings("unchecked")
    private String extractTenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
