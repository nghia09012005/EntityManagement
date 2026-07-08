package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "unknown_field_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "eventType_fieldName", def = "{'eventType': 1, 'fieldName': 1}", unique = true)
public class UnknownFieldStat {

    @Id
    private String id;
    private String eventType;
    private String fieldName;
    private long count;
    private String sampleValue;
    private LocalDateTime lastSeen;
    private String lastEventId;
}
