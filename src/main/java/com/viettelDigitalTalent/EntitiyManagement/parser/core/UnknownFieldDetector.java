package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.CustomEventTypeRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.FieldMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UnknownFieldDetector {

    private final ObjectMapper objectMapper;
    private final CustomEventTypeRepository customEventTypeRepository;
    private final FieldMappingRepository fieldMappingRepository;

    public Map<String, Object> detect(String rawJson, String eventType) {
        return detect(rawJson, eventType, "default");
    }

    public Map<String, Object> detect(String rawJson, String eventType, String tenantId) {
        if (rawJson == null || !rawJson.trim().startsWith("{")) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isObject()) return Map.of();

            Set<String> known = new HashSet<>(KnownEventFields.forEventType(eventType));
            addCustomFieldNames(known, eventType, tenantId);
            addEnabledMappedFieldNames(known, eventType, tenantId);
            Map<String, Object> unknown = new LinkedHashMap<>();

            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (!known.contains(key)) {
                    Object value = scalarValue(entry.getValue());
                    if (value != null) unknown.put(key, value);
                }
            });

            return unknown;
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, Object> collectCustomFieldValues(String rawJson, String eventType, String tenantId) {
        if (rawJson == null || !rawJson.trim().startsWith("{")) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isObject()) return Map.of();

            Map<String, Object> customValues = new LinkedHashMap<>();
            customEventTypeRepository.findByTenantIdAndEventType(tenantId == null ? "default" : tenantId,
                            normalizeEventType(eventType)).ifPresent(customEventType -> {
                if (customEventType.getFields() == null) return;
                customEventType.getFields().forEach(field -> {
                    if (field == null || field.getName() == null || field.getName().isBlank()) return;
                    JsonNode node = root.get(field.getName());
                    if (node != null && !node.isNull()) {
                        customValues.put(field.getName(), scalarValue(node));
                    }
                });
            });
            return customValues;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void addCustomFieldNames(Set<String> known, String eventType, String tenantId) {
        if (tenantId == null) tenantId = "default";
        customEventTypeRepository.findByTenantIdAndEventType(tenantId, normalizeEventType(eventType))
                .ifPresent(customEventType -> {
                    if (customEventType.getFields() == null) return;
                    customEventType.getFields().forEach(field -> {
                        if (field != null && field.getName() != null && !field.getName().isBlank()) {
                            known.add(field.getName());
                        }
                    });
                });
    }

    private void addEnabledMappedFieldNames(Set<String> known, String eventType, String tenantId) {
        if (tenantId == null) tenantId = "default";
        fieldMappingRepository.findByTenantIdAndEventTypeAndEnabledTrue(tenantId, normalizeEventType(eventType))
                .forEach(mapping -> {
                    if (mapping != null && mapping.getSourceField() != null && !mapping.getSourceField().isBlank()) {
                        known.add(mapping.getSourceField());
                    }
                });
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? null : eventType.trim().toUpperCase(Locale.ROOT);
    }

    private Object scalarValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray() || node.isObject()) return node.toString();
        return node.asText(null);
    }
}
