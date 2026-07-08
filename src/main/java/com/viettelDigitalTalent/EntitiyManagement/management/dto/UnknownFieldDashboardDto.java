package com.viettelDigitalTalent.EntitiyManagement.management.dto;

import java.util.List;

public record UnknownFieldDashboardDto(
        String eventType,
        List<FieldStat> topFields
) {
    public record FieldStat(
            String fieldName,
            long count,
            String sampleValue,
            String lastSeen,
            String lastEventId,
            List<String> eventIds
    ) {}
}
