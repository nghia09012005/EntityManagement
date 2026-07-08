package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "field_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMapping {

    @Id
    private String id;
    private String tenantId;
    /** AUTHENTICATION | PROCESS | NETWORK | THREAT */
    private String eventType;
    /** Tên trường lạ trong raw JSON */
    private String sourceField;
    /** User | Host | IP | Domain | FileHash | Url | Process | CloudResource | Email | Cve */
    private String entityType;
    /** Loại quan hệ Neo4j, ví dụ ACCESSED, CONNECTED_TO */
    private String relationshipType;
    /** Entity type của node đích/nguồn liên kết */
    private String relatedEntityType;
    /** Trường đã biết trên event để lấy giá trị entity liên kết (targetUser, username, …) */
    private String relatedEventField;
    /** FROM_RELATED: relatedEntity -[rel]-> customEntity | TO_RELATED: customEntity -[rel]-> relatedEntity */
    private String relationshipDirection = "FROM_RELATED";
    private boolean enabled = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
