package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.FieldMapping;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.CustomEventTypeRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.FieldMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/field-mappings")
@RequiredArgsConstructor
public class FieldMappingController {

    private static final Set<String> VALID_EVENT_TYPES = Set.of(
            "AUTHENTICATION", "PROCESS", "NETWORK", "THREAT");
    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
            "User", "Host", "IP", "Domain", "FileHash", "Url", "Process", "CloudResource", "Email", "Cve");
    private static final Set<String> VALID_DIRECTIONS = Set.of("FROM_RELATED", "TO_RELATED");

    private final FieldMappingRepository fieldMappingRepository;
    private final CustomEventTypeRepository customEventTypeRepository;

    @GetMapping
    public ResponseEntity<List<FieldMapping>> list(Authentication authentication) {
        return ResponseEntity.ok(fieldMappingRepository.findByTenantIdOrderByCreatedAtDesc(tenantId(authentication)));
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody FieldMapping body, Authentication authentication) {
        String error = validate(body);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

        body.setId(null);
        body.setTenantId(tenantId(authentication));
        body.setEventType(normalizeEventType(body.getEventType()));
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        if (body.getRelationshipDirection() == null) body.setRelationshipDirection("FROM_RELATED");

        return ResponseEntity.ok(fieldMappingRepository.save(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id,
                                         @RequestBody FieldMapping body,
                                         Authentication authentication) {
        return (ResponseEntity<Object>) fieldMappingRepository.findById(id).map(existing -> {
            if (!tenantId(authentication).equals(existing.getTenantId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }

            String error = validate(body);
            if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));

            existing.setEventType(normalizeEventType(body.getEventType()));
            existing.setSourceField(body.getSourceField());
            existing.setEntityType(body.getEntityType());
            existing.setRelationshipType(body.getRelationshipType());
            existing.setRelatedEntityType(body.getRelatedEntityType());
            existing.setRelatedEventField(body.getRelatedEventField());
            existing.setRelationshipDirection(
                    body.getRelationshipDirection() != null ? body.getRelationshipDirection() : "FROM_RELATED");
            existing.setEnabled(body.isEnabled());
            existing.setUpdatedAt(LocalDateTime.now());

            return ResponseEntity.ok(fieldMappingRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id, Authentication authentication) {
        return (ResponseEntity<Object>) fieldMappingRepository.findById(id).map(existing -> {
            if (!tenantId(authentication).equals(existing.getTenantId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }
            fieldMappingRepository.delete(existing);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(Authentication authentication) {
        String tenantId = tenantId(authentication);
        LinkedHashSet<String> eventTypes = new LinkedHashSet<>(VALID_EVENT_TYPES);
        customEventTypeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .forEach(customType -> {
                    if (customType.getEventType() != null && !customType.getEventType().isBlank()) {
                        eventTypes.add(customType.getEventType());
                    }
                });

        Map<String, List<String>> relatedEventFields = new LinkedHashMap<>();
        relatedEventFields.put("AUTHENTICATION", List.of("username", "ipAddress", "workstation"));
        relatedEventFields.put("PROCESS", List.of("processName", "processPath", "fileHash", "commandLine", "hostname"));
        relatedEventFields.put("NETWORK", List.of("srcIp", "dstIp", "dstDomain"));
        relatedEventFields.put("THREAT", List.of("targetUser", "targetIp", "targetHost", "targetDomain",
                "targetFileHash", "targetProcess", "targetUrl", "targetCloudResourceId",
                "targetEmail", "targetCve", "sourceIp", "sourceHost", "sourceDomain"));

        customEventTypeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .forEach(customType -> {
                    List<String> names = new ArrayList<>();
                    if (customType.getFields() != null) {
                        customType.getFields().forEach(field -> {
                            if (field != null && field.getName() != null && !field.getName().isBlank()) {
                                names.add(field.getName());
                            }
                        });
                    }
                    if (!names.isEmpty()) {
                        relatedEventFields.put(customType.getEventType(), names);
                    }
                });

        return ResponseEntity.ok(Map.of(
                "eventTypes", new ArrayList<>(eventTypes),
                "entityTypes", VALID_ENTITY_TYPES,
                "relationshipTypes", List.of(
                        "LOGGED_IN_TO", "AUTHENTICATED_TO", "EXECUTED_ON", "HASH_OF",
                        "CONNECTED_TO", "RESOLVES_TO", "ALERTED_FROM", "TARGETED_AT",
                        "DETECTED_ON", "ACCESSED", "HAS_EMAIL", "AFFECTS", "SAME_AS"),
                "relationshipDirections", VALID_DIRECTIONS,
                "relatedEventFields", relatedEventFields
        ));
    }

    private String validate(FieldMapping body) {
        if (body.getEventType() == null || body.getEventType().isBlank()) {
            return "eventType là bắt buộc";
        }
        if (body.getSourceField() == null || body.getSourceField().isBlank()) {
            return "sourceField là bắt buộc";
        }
        if (body.getEntityType() == null || !VALID_ENTITY_TYPES.contains(body.getEntityType())) {
            return "entityType không hợp lệ";
        }
        if (body.getRelationshipType() != null && !body.getRelationshipType().isBlank()) {
            if (body.getRelatedEntityType() == null || !VALID_ENTITY_TYPES.contains(body.getRelatedEntityType())) {
                return "relatedEntityType không hợp lệ khi có relationshipType";
            }
            if (body.getRelationshipDirection() != null
                    && !VALID_DIRECTIONS.contains(body.getRelationshipDirection())) {
                return "relationshipDirection không hợp lệ";
            }
        }
        return null;
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? null : eventType.trim().toUpperCase(Locale.ROOT);
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
