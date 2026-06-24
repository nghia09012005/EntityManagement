package com.viettelDigitalTalent.EntitiyManagement.storage.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "dlq_events")
public class DlqEvent {

    @Id
    private String id;

    @Indexed
    private String sourceTopic;

    private String originalPayload;

    private String error;

    private String errorClass;

    @Indexed
    private LocalDateTime failedAt;
}
