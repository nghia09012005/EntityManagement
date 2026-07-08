package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "custom_event_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomEventType {

    @Id
    private String id;
    private String tenantId;
    private String eventType;
    private List<FieldDefinition> fields = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinition {
        private String name;
        private String valueType;
        private String entityType;
        private String relationshipType;
        private String relatedEntityType;
        private String relatedEventField;
        private String relationshipDirection = "FROM_RELATED";
    }
}
