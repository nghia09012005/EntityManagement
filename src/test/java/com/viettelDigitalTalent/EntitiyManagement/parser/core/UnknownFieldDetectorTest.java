package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.CustomEventType;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.FieldMapping;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.CustomEventTypeRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.FieldMappingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownFieldDetectorTest {

    @Test
    void treatsCustomFieldDefinitionsAsKnownFields() {
        CustomEventTypeRepository customEventTypeRepository = customEventTypeRepository(Optional.empty());
        FieldMappingRepository fieldMappingRepository = fieldMappingRepository(List.of());
        UnknownFieldDetector detector = new UnknownFieldDetector(new ObjectMapper(), customEventTypeRepository, fieldMappingRepository);

        CustomEventType customEventType = new CustomEventType();
        customEventType.setEventType("MFA_FAILURE");
        customEventType.setFields(List.of(
                new CustomEventType.FieldDefinition("device_id", "string", "Host", null, null, null, "FROM_RELATED"),
                new CustomEventType.FieldDefinition("risk_score", "number", "User", null, null, null, "FROM_RELATED")
        ));

        customEventTypeRepository = customEventTypeRepository(Optional.of(customEventType));
        detector = new UnknownFieldDetector(new ObjectMapper(), customEventTypeRepository, fieldMappingRepository);

        Map<String, Object> unknown = detector.detect(
                "{\"eventType\":\"MFA_FAILURE\",\"device_id\":\"abc\",\"risk_score\":5,\"suspicious_flag\":true}",
                "MFA_FAILURE"
        );

        assertThat(unknown).containsOnlyKeys("suspicious_flag");
        assertThat(unknown.get("suspicious_flag")).isEqualTo(true);
    }

    @Test
    void treatsOnlyEnabledFieldMappingsAsKnownFields() {
        FieldMapping enabledMapping = new FieldMapping();
        enabledMapping.setSourceField("mapped_host");
        enabledMapping.setEnabled(true);

        UnknownFieldDetector detector = new UnknownFieldDetector(
                new ObjectMapper(),
                customEventTypeRepository(Optional.empty()),
                fieldMappingRepository(List.of(enabledMapping))
        );

        Map<String, Object> unknown = detector.detect(
                "{\"eventType\":\"AUTHENTICATION\",\"username\":\"nghia\",\"mapped_host\":\"ws01\",\"disabled_field\":\"x\"}",
                "AUTHENTICATION"
        );

        assertThat(unknown).containsOnlyKeys("disabled_field");
    }

    private CustomEventTypeRepository customEventTypeRepository(Optional<CustomEventType> response) {
        return (CustomEventTypeRepository) Proxy.newProxyInstance(
                CustomEventTypeRepository.class.getClassLoader(),
                new Class<?>[]{CustomEventTypeRepository.class},
                (proxy, method, args) -> {
                    if ("findByTenantIdAndEventType".equals(method.getName())) return response;
                    if ("existsByTenantIdAndEventType".equals(method.getName())) return false;
                    if ("findByTenantIdOrderByCreatedAtDesc".equals(method.getName())) return List.of();
                    if ("toString".equals(method.getName())) return "CustomEventTypeRepositoryStub";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
    }

    private FieldMappingRepository fieldMappingRepository(List<FieldMapping> response) {
        return (FieldMappingRepository) Proxy.newProxyInstance(
                FieldMappingRepository.class.getClassLoader(),
                new Class<?>[]{FieldMappingRepository.class},
                (proxy, method, args) -> {
                    if ("findByTenantIdAndEventTypeAndEnabledTrue".equals(method.getName())) return response;
                    if ("findByEventTypeAndEnabledTrue".equals(method.getName())) return response;
                    if ("findByTenantIdOrderByCreatedAtDesc".equals(method.getName())) return List.of();
                    if ("toString".equals(method.getName())) return "FieldMappingRepositoryStub";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
    }
}
