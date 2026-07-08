package com.viettelDigitalTalent.EntitiyManagement.management.dto;

import java.util.List;

public record UnknownFieldEventPageDto(
        List<UnknownFieldEventDto> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public record UnknownFieldEventDto(
            String eventId,
            String eventType,
            String fieldName,
            String sampleValue,
            String rawPayload,
            String occurredAt
    ) {}
}
