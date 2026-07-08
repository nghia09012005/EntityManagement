package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "unknown_field_occurrences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnknownFieldOccurrence {

    @Id
    private String id;
    private String tenantId;
    private String eventType;
    private String fieldName;
    private String eventId;
    private String sampleValue;
    private LocalDateTime occurredAt;
}