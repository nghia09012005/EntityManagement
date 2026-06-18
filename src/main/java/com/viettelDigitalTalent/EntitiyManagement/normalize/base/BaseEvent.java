package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent.class, name = "AUTHENTICATION"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent.class, name = "PROCESS"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent.class, name = "NETWORK"),
    @JsonSubTypes.Type(value = com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent.class, name = "ALERT")
})
public class BaseEvent {
    private String eventId;            // UUID duy nhất
    private String source;             // e.g., "winlogbeat", "syslog"
    private String category;           // e.g., "AUTHENTICATION", "PROCESS"
    private LocalDateTime timestamp;   // Thời gian thực của log

    // Phần Enrichment
    private String enrichmentId;       // Link tới MongoDB
    private boolean isEnriched;        // Flag xác nhận đã làm giàu

    // Metadata thô (đề phòng trường hợp log có field lạ)
    private Map<String, Object> rawData = new HashMap<>();
}
