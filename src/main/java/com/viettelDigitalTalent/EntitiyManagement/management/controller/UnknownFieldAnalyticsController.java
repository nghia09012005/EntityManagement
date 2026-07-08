package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.ChartStatsDto;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldOccurrence;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UnknownFieldOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unknown-fields")
@RequiredArgsConstructor
public class UnknownFieldAnalyticsController {

    private final UnknownFieldOccurrenceRepository occurrenceRepository;

    @GetMapping("/analytics")
    public ResponseEntity<ChartStatsDto> analytics(Authentication authentication,
                                                   @RequestParam(defaultValue = "24h") String range) {
        String tenantId = tenantId(authentication);
        List<UnknownFieldOccurrence> occurrences = occurrenceRepository.findAll(tenantId);

        RangeSpec spec = resolveRange(range);
        LocalDateTime cutoff = LocalDateTime.now().minus(spec.amount(), spec.unit());

        Map<String, Long> byHour = new LinkedHashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();

        occurrences.stream()
                .filter(o -> o.getOccurredAt() != null)
                .filter(o -> spec.olderThan() ? o.getOccurredAt().isBefore(cutoff) : !o.getOccurredAt().isBefore(cutoff))
                .forEach(o -> {
                    String bucket = bucketKey(o.getOccurredAt(), spec);
                    byHour.merge(bucket, 1L, Long::sum);
                    String type = o.getEventType() != null ? o.getEventType() : "UNKNOWN";
                    byType.merge(type, 1L, Long::sum);
                });

        List<ChartStatsDto.TimePoint> line = byHour.entrySet().stream()
                .map(e -> new ChartStatsDto.TimePoint(e.getKey(), e.getValue()))
                .toList();

        List<ChartStatsDto.PiePoint> pie = byType.entrySet().stream()
                .map(e -> new ChartStatsDto.PiePoint(e.getKey(), e.getValue()))
                .toList();

        return ResponseEntity.ok(new ChartStatsDto(line, pie));
    }

    private RangeSpec resolveRange(String range) {
        return switch (range == null ? "" : range.trim().toLowerCase()) {
            case "7d" -> new RangeSpec(7, ChronoUnit.DAYS, false);
            case "30d" -> new RangeSpec(30, ChronoUnit.DAYS, false);
            case "30d+" -> new RangeSpec(30, ChronoUnit.DAYS, true);
            default -> new RangeSpec(24, ChronoUnit.HOURS, false);
        };
    }

    private String bucketKey(LocalDateTime timestamp, RangeSpec spec) {
        if (spec.unit() == ChronoUnit.HOURS) {
            return timestamp.truncatedTo(ChronoUnit.HOURS).toString();
        }
        return timestamp.toLocalDate().toString();
    }

    private record RangeSpec(long amount, ChronoUnit unit, boolean olderThan) {}

    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
