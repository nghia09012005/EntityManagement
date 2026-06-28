package com.viettelDigitalTalent.EntitiyManagement.queue.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.DlqEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqEventRepository dlqEventRepository;

    @GetMapping("/events")
    public ResponseEntity<Page<DlqEvent>> listEvents(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(
                dlqEventRepository.findAllByTenantIdOrderByFailedAtDesc(
                        tenantId(authentication), PageRequest.of(page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary(Authentication authentication) {
        String tid = tenantId(authentication);
        Map<String, Long> counts = Map.of(
                "raw-logs",          dlqEventRepository.countBySourceTopicAndTenantId("raw-logs", tid),
                "normalized-events", dlqEventRepository.countBySourceTopicAndTenantId("normalized-events", tid),
                "enriched-events",   dlqEventRepository.countBySourceTopicAndTenantId("enriched-events", tid),
                "total",             dlqEventRepository.countByTenantId(tid)
        );
        return ResponseEntity.ok(counts);
    }

    @SuppressWarnings("unchecked")
    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
