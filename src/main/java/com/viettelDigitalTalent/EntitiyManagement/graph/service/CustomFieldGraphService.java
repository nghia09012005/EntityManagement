package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.FieldMapping;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.CustomEventTypeRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.FieldMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldGraphService {

    private static final String REL_UPSERT = """
            ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                          r.firstEventId = $eventId, r.lastEventId = $eventId
            ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                          r.lastEventId = $eventId
            """;

    private record EntityMeta(String label, String idProperty, Function<String, String> normalizer) {}

    private static final Map<String, EntityMeta> ENTITIES = Map.of(
            "User",          new EntityMeta("User",          "username",   EntityNormalizer::username),
            "Host",          new EntityMeta("Host",          "hostname",   EntityNormalizer::hostname),
            "IP",            new EntityMeta("IP",            "address",    EntityNormalizer::ip),
            "Domain",        new EntityMeta("Domain",        "name",       EntityNormalizer::domain),
            "FileHash",      new EntityMeta("FileHash",      "hash",       EntityNormalizer::hash),
            "Url",           new EntityMeta("Url",           "url",        EntityNormalizer::url),
            "Process",       new EntityMeta("Process",       "name",       EntityNormalizer::processName),
            "CloudResource", new EntityMeta("CloudResource", "resourceId", s -> s != null ? s.trim() : null),
            "Email",         new EntityMeta("Email",         "address",    EntityNormalizer::email),
            "Cve",           new EntityMeta("Cve",           "cveId",      EntityNormalizer::cveId)
    );

    private final FieldMappingRepository fieldMappingRepository;
    private final CustomEventTypeRepository customEventTypeRepository;
    private final Neo4jClient neo4jClient;

    public void applyMappings(BaseEvent event) {
        String eventType = event.getCategory();
        String tenantId = event.getTenantId() != null ? event.getTenantId() : "default";
        LocalDateTime now = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
        String eventId = event.getEventId();

        Map<String, Object> unknown = event.getUnknownFields();
        if (unknown != null && !unknown.isEmpty()) {
            List<FieldMapping> mappings = fieldMappingRepository.findByTenantIdAndEventTypeAndEnabledTrue(tenantId, eventType);
            for (FieldMapping mapping : mappings) {
                if (!mapping.isEnabled()) continue;
                Object raw = unknown.get(mapping.getSourceField());
                if (raw == null) continue;

                EntityMeta customMeta = ENTITIES.get(mapping.getEntityType());
                if (customMeta == null) {
                    log.warn("[CustomFieldGraph] Unknown entity type: {}", mapping.getEntityType());
                    continue;
                }

                String customValue = customMeta.normalizer().apply(String.valueOf(raw));
                if (customValue == null || customValue.isBlank()) continue;

                mergeNode(customMeta, customValue, tenantId);

                if (mapping.getRelationshipType() != null && !mapping.getRelationshipType().isBlank()
                        && mapping.getRelatedEntityType() != null) {
                    EntityMeta relatedMeta = ENTITIES.get(mapping.getRelatedEntityType());
                    if (relatedMeta == null) continue;

                    String relatedRaw;
                    if (mapping.getRelatedEventField() != null && !mapping.getRelatedEventField().isBlank()) {
                        relatedRaw = EventFieldAccessor.getValue(event, mapping.getRelatedEventField());
                    } else {
                        relatedRaw = inferRelatedRawFromMappings(event, mappings,
                                mapping.getRelatedEntityType(), mapping.getSourceField());
                        if (relatedRaw == null) {
                            relatedRaw = inferRelatedRaw(event, mapping.getRelatedEntityType());
                        }
                    }
                    if (relatedRaw == null) continue;

                    String relatedValue = relatedMeta.normalizer().apply(relatedRaw);
                    if (relatedValue == null || relatedValue.isBlank()) continue;

                    createRelationship(mapping.getRelationshipType(), mapping.getRelationshipDirection(),
                            relatedMeta, relatedValue, customMeta, customValue, tenantId, now.toString(), eventId,
                            mapping.getSourceField());
                }
            }
        }

        String customEventType = customEventType(event, eventType);
        customEventTypeRepository.findByTenantIdAndEventType(tenantId, customEventType)
                .ifPresent(customType -> applyCustomEventType(event, customType, tenantId, now, eventId));
    }

    private String customEventType(BaseEvent event, String fallback) {
        if (event.getRawData() != null) {
            Object customType = event.getRawData().get("customEventType");
            if (customType != null && !String.valueOf(customType).isBlank()) {
                return String.valueOf(customType).trim().toUpperCase();
            }
            Object eventType = event.getRawData().get("eventType");
            if (eventType != null && !String.valueOf(eventType).isBlank()) {
                return String.valueOf(eventType).trim().toUpperCase();
            }
        }
        return fallback;
    }

    private void applyCustomEventType(BaseEvent event, CustomEventType customType, String tenantId, LocalDateTime now, String eventId) {
        if (customType.getFields() == null || customType.getFields().isEmpty()) return;

        for (CustomEventType.FieldDefinition field : customType.getFields()) {
            Object raw = resolveFieldValue(event, field.getName());
            if (raw == null) continue;

            EntityMeta fieldMeta = field.getEntityType() == null || field.getEntityType().isBlank()
                    ? null : ENTITIES.get(field.getEntityType());
            if (fieldMeta != null) {
                String fieldValue = fieldMeta.normalizer().apply(String.valueOf(raw));
                if (fieldValue != null && !fieldValue.isBlank()) {
                    mergeNode(fieldMeta, fieldValue, tenantId);
                }
            }

            if (field.getRelationshipType() != null && !field.getRelationshipType().isBlank()) {
                if (fieldMeta == null) {
                    log.warn("[CustomFieldGraph] Skip relationship for field {} because entityType is missing", field.getName());
                    continue;
                }

                String fieldValue = fieldMeta.normalizer().apply(String.valueOf(raw));
                if (fieldValue == null || fieldValue.isBlank()) continue;

                CustomEventType.FieldDefinition relatedFieldDef = findField(customType, field.getRelatedEventField());
                if (relatedFieldDef == null) {
                    relatedFieldDef = findFieldByEntityType(customType, field.getRelatedEntityType(), field.getName());
                }
                EntityMeta relatedMeta = relatedFieldDef != null && relatedFieldDef.getEntityType() != null && !relatedFieldDef.getEntityType().isBlank()
                        ? ENTITIES.get(relatedFieldDef.getEntityType())
                        : (field.getRelatedEntityType() != null && !field.getRelatedEntityType().isBlank() ? ENTITIES.get(field.getRelatedEntityType()) : null);
                if (relatedMeta == null) continue;

                Object relatedRaw;
                String relatedFieldName = relatedFieldDef != null ? relatedFieldDef.getName() : field.getRelatedEventField();
                if (relatedFieldName != null && !relatedFieldName.isBlank()) {
                    relatedRaw = resolveFieldValue(event, relatedFieldName);
                } else {
                    relatedRaw = inferRelatedRaw(event, field.getRelatedEntityType());
                }
                if (relatedRaw == null) continue;

                String relatedValue = relatedMeta.normalizer().apply(String.valueOf(relatedRaw));
                if (relatedValue == null || relatedValue.isBlank()) continue;

                mergeNode(relatedMeta, relatedValue, tenantId);
                createRelationship(field.getRelationshipType(), field.getRelationshipDirection(),
                        relatedMeta, relatedValue, fieldMeta, fieldValue, tenantId, now.toString(), eventId,
                        field.getName());
            }
        }
    }

    private CustomEventType.FieldDefinition findField(CustomEventType customType, String fieldName) {
        if (customType.getFields() == null || fieldName == null || fieldName.isBlank()) return null;
        return customType.getFields().stream()
                .filter(field -> field != null && fieldName.equals(field.getName()))
                .findFirst()
                .orElse(null);
    }

    private CustomEventType.FieldDefinition findFieldByEntityType(CustomEventType customType,
                                                                  String entityType,
                                                                  String excludeFieldName) {
        if (customType.getFields() == null || entityType == null || entityType.isBlank()) return null;
        return customType.getFields().stream()
                .filter(field -> field != null)
                .filter(field -> field.getName() != null && !field.getName().equals(excludeFieldName))
                .filter(field -> entityType.equals(field.getEntityType()))
                .findFirst()
                .orElse(null);
    }

    private String inferRelatedRawFromMappings(BaseEvent event,
                                               List<FieldMapping> mappings,
                                               String relatedEntityType,
                                               String excludeSourceField) {
        if (event == null || mappings == null || relatedEntityType == null || relatedEntityType.isBlank()) return null;

        for (FieldMapping candidate : mappings) {
            if (candidate == null || !candidate.isEnabled()) continue;
            if (candidate.getSourceField() == null || candidate.getSourceField().isBlank()) continue;
            if (candidate.getSourceField().equals(excludeSourceField)) continue;
            if (!relatedEntityType.equals(candidate.getEntityType())) continue;

            Object raw = resolveFieldValue(event, candidate.getSourceField());
            if (raw != null && !String.valueOf(raw).isBlank()) {
                return String.valueOf(raw);
            }
        }
        return null;
    }

    private Object resolveFieldValue(BaseEvent event, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return null;

        // Known alias groups — try all aliases in a matching group if the requested name belongs to it,
        // otherwise only try the requested name.
        List<List<String>> aliasGroups = List.of(
                Arrays.asList("username", "user", "targetUser", "accountName", "actor"),
                Arrays.asList("hostname", "host", "targetHost", "workstation"),
                Arrays.asList("address", "ip", "srcIp", "dstIp", "targetIp", "srcEndpoint.ip"),
                Arrays.asList("name", "domain", "dstDomain", "targetDomain"),
                Arrays.asList("hash", "fileHash", "targetFileHash"),
                Arrays.asList("url", "targetUrl"),
                Arrays.asList("processName", "name", "procName"),
                Arrays.asList("resourceId", "cloud_resource_id", "targetCloudResourceId"),
                Arrays.asList("address", "email", "targetEmail"),
                Arrays.asList("cveId", "targetCve")
        );

        List<String> candidates = null;
        for (List<String> group : aliasGroups) {
            if (group.stream().anyMatch(s -> s.equalsIgnoreCase(fieldName))) {
                candidates = group;
                break;
            }
        }
        if (candidates == null) candidates = List.of(fieldName);

        // Try each candidate against rawData, unknownFields, then EventFieldAccessor
        if (event.getRawData() != null) {
            for (String c : candidates) {
                if (event.getRawData().containsKey(c)) {
                    Object v = event.getRawData().get(c);
                    if (v != null) return v;
                }
            }
        }

        if (event.getUnknownFields() != null) {
            for (String c : candidates) {
                if (event.getUnknownFields().containsKey(c)) {
                    Object v = event.getUnknownFields().get(c);
                    if (v != null) return v;
                }
            }
        }

        for (String c : candidates) {
            Object v = EventFieldAccessor.getValue(event, c);
            if (v != null) return v;
        }

        return null;
    }

    private String inferRelatedRaw(BaseEvent event, String relatedEntityType) {
        if (event == null || relatedEntityType == null || relatedEntityType.isBlank()) return null;
        return switch (relatedEntityType) {
            case "IP" -> firstNonNull(
                    resolveFieldValue(event, "ip"),
                    resolveFieldValue(event, "ipAddress"),
                    resolveFieldValue(event, "srcIp"),
                    resolveFieldValue(event, "dstIp"),
                    resolveFieldValue(event, "sourceIp"),
                    resolveFieldValue(event, "targetIp"),
                    resolveFieldValue(event, "address")
            );
            case "User" -> firstNonNull(
                    resolveFieldValue(event, "username"),
                    resolveFieldValue(event, "user"),
                    resolveFieldValue(event, "targetUser"),
                    resolveFieldValue(event, "actor")
            );
            case "Host" -> firstNonNull(
                    resolveFieldValue(event, "hostname"),
                    resolveFieldValue(event, "workstation"),
                    resolveFieldValue(event, "host"),
                    resolveFieldValue(event, "targetHost"),
                    resolveFieldValue(event, "sourceHost")
            );
            case "Domain" -> firstNonNull(
                    resolveFieldValue(event, "name"),
                    resolveFieldValue(event, "domain"),
                    resolveFieldValue(event, "dstDomain"),
                    resolveFieldValue(event, "targetDomain")
            );
            case "FileHash" -> firstNonNull(
                    resolveFieldValue(event, "fileHash"),
                    resolveFieldValue(event, "hash"),
                    resolveFieldValue(event, "targetFileHash")
            );
            case "Url" -> firstNonNull(
                    resolveFieldValue(event, "url"),
                    resolveFieldValue(event, "targetUrl")
            );
            case "Process" -> firstNonNull(
                    resolveFieldValue(event, "processName"),
                    resolveFieldValue(event, "name"),
                    resolveFieldValue(event, "procName"),
                    resolveFieldValue(event, "processPath")
            );
            case "CloudResource" -> firstNonNull(
                    resolveFieldValue(event, "resourceId"),
                    resolveFieldValue(event, "cloud_resource_id"),
                    resolveFieldValue(event, "targetCloudResourceId")
            );
            case "Email" -> firstNonNull(
                    resolveFieldValue(event, "email"),
                    resolveFieldValue(event, "address"),
                    resolveFieldValue(event, "targetEmail")
            );
            case "Cve" -> firstNonNull(
                    resolveFieldValue(event, "cveId"),
                    resolveFieldValue(event, "targetCve")
            );
            default -> null;
        };
    }

    private static String firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value == null) continue;
            String candidate = String.valueOf(value);
            if (!candidate.isBlank()) return candidate;
        }
        return null;
    }

    private void mergeNode(EntityMeta meta, String value, String tenantId) {
        Map<String, Object> p = new HashMap<>();
        p.put("tenantId", tenantId);
        p.put("value", value);

        String cypher = "MERGE (n:" + meta.label() + " {tenantId: $tenantId, "
                + meta.idProperty() + ": $value})";

        neo4jClient.query(cypher).bindAll(p).run();
    }

    private void createRelationship(String relationshipType,
                                    String relationshipDirection,
                                    EntityMeta relatedMeta, String relatedValue,
                                    EntityMeta customMeta, String customValue,
                                    String tenantId, String now, String eventId,
                                    String sourceField) {
        String relType = relationshipType.toUpperCase().replaceAll("[^A-Z0-9_]", "");
        if (relType.isBlank()) return;

        boolean fromRelated = !"TO_RELATED".equalsIgnoreCase(relationshipDirection);

        EntityMeta fromMeta = fromRelated ? relatedMeta : customMeta;
        String fromValue = fromRelated ? relatedValue : customValue;
        EntityMeta toMeta = fromRelated ? customMeta : relatedMeta;
        String toValue = fromRelated ? customValue : relatedValue;

        Map<String, Object> p = new HashMap<>();
        p.put("tenantId", tenantId);
        p.put("fromValue", fromValue);
        p.put("toValue", toValue);
        p.put("now", now);
        p.put("eventId", eventId);

        String cypher = """
                MATCH (a:%s {tenantId: $tenantId, %s: $fromValue})
                MATCH (b:%s {tenantId: $tenantId, %s: $toValue})
                MERGE (a)-[r:%s]->(b)
                """.formatted(
                fromMeta.label(), fromMeta.idProperty(),
                toMeta.label(), toMeta.idProperty(),
                relType) + REL_UPSERT;

        neo4jClient.query(cypher).bindAll(p).run();
        log.info("[CustomFieldGraph] {} -[{}]-> {} via field '{}'",
                fromMeta.label(), relType, toMeta.label(), sourceField);
    }
}
