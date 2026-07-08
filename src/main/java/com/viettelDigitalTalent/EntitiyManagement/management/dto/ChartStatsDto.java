package com.viettelDigitalTalent.EntitiyManagement.management.dto;

import java.util.List;

public record ChartStatsDto(
        List<TimePoint> line,
        List<PiePoint> pie
) {
    public record TimePoint(String bucket, long count) {}
    public record PiePoint(String label, long count) {}
}