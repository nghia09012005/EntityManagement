package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BaseEvent {
    private String eventId;            // UUID duy nhất
    private String source;             // e.g., "winlogbeat", "syslog"
    private String category;           // e.g., "AUTHENTICATION", "PROCESS"
    private LocalDateTime timestamp;   // Thời gian thực của log

    // Phần Enrichment
    private String enrichmentId;       // Link tới MongoDB
    private boolean isEnriched;        // Flag xác nhận đã làm giàu

    // Metadata thô (đề phòng trường hợp log có field lạ)
    private Map<String, Object> rawData;
}
