package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.CustomEventTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/custom-event-types")
@RequiredArgsConstructor
public class CustomEventTypeController {

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
            "User", "Host", "IP", "Domain", "FileHash", "Url", "Process", "CloudResource", "Email", "Cve");
    private static final Set<String> VALID_DIRECTIONS = Set.of("FROM_RELATED", "TO_RELATED");

    private final CustomEventTypeRepository customEventTypeRepository;

    @GetMapping
    public ResponseEntity<List<CustomEventType>> list(Authentication authentication) {
        return ResponseEntity.ok(customEventTypeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId(authentication)));
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody CustomEventType body, Authentication authentication) {
        String eventType = normalizeEventType(body.getEventType());
        if (eventType == null || eventType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventType là bắt buộc"));
        }
        if (customEventTypeRepository.existsByTenantIdAndEventType(tenantId(authentication), eventType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventType đã tồn tại"));
        }
        if (body.getFields() == null) {
            body.setFields(new ArrayList<>());
        }
        for (CustomEventType.FieldDefinition field : body.getFields()) {
            String error = validateField(field);
            if (error != null) {
                return ResponseEntity.badRequest().body(Map.of("error", error));
            }
        }
        body.setEventType(eventType);
        body.setId(null);
        body.setTenantId(tenantId(authentication));
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(customEventTypeRepository.save(body));
    }

    private String validateField(CustomEventType.FieldDefinition field) {
        if (field == null || field.getName() == null || field.getName().isBlank()) {
            return "Tên trường không được để trống";
        }
        if (field.getEntityType() != null && !field.getEntityType().isBlank()
                && !VALID_ENTITY_TYPES.contains(field.getEntityType())) {
            return "entityType không hợp lệ";
        }
        if (field.getRelationshipType() != null && !field.getRelationshipType().isBlank()) {
            if (field.getEntityType() == null || field.getEntityType().isBlank()) {
                return "entityType là bắt buộc khi có relationshipType";
            }
            if (field.getRelatedEntityType() == null || field.getRelatedEntityType().isBlank()) {
                return "relatedEntityType là bắt buộc khi có relationshipType";
            }
            if (field.getRelationshipDirection() != null && !VALID_DIRECTIONS.contains(field.getRelationshipDirection())) {
                return "relationshipDirection không hợp lệ";
            }
        }
        return null;
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? null : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
