package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "incidents")
@CompoundIndex(name = "pattern_window_tenant_idx", def = "{'patternName': 1, 'windowStart': 1, 'tenantId': 1}", unique = true)
@Data
@NoArgsConstructor
public class Incident {
    @Id
    private String id;
    private String tenantId;
    private String patternName;
    private String mitreId;
    private String title;
    private String severity;
    private String status = "NEW";
    private Map<String, List<String>> affectedEntities;
    private List<String> relatedEventIds;
    private List<Map<String, String>> timeline;
    private List<String> recommendedActions;
    private LocalDateTime windowStart;
    private LocalDateTime detectedAt;
    private LocalDateTime updatedAt;
}