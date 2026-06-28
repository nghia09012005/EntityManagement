package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    private String eventId;
    private String tenantId;
    private String source;
    private String category;
    private LocalDateTime timestamp;
    private Map<String, Object> rawData;
    private String rawEvent;
    private boolean isEnriched;
    private Map<String, Object> enrichment;
}
