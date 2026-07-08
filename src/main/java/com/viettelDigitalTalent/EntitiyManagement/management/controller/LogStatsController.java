package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.ChartStatsDto;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
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
import java.util.Set;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogStatsController {

    private static final Set<String> BUILT_IN_EVENT_TYPES = Set.of(
            "AUTHENTICATION", "PROCESS", "NETWORK", "ALERT", "THREAT", "SECURITY_FINDING"
    );

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    public ResponseEntity<ChartStatsDto> stats(Authentication authentication, @RequestParam(defaultValue = "24h") String range) {
        String tenantId = tenantId(authentication);
        LocalDateTime since = LocalDateTime.of(1970, 1, 1, 0, 0);
        List<AuditLog> logs = auditLogRepository.findRecentEvents(since, tenantId);

        RangeSpec spec = resolveRange(range);
        LocalDateTime cutoff = LocalDateTime.now().minus(spec.amount(), spec.unit());

        Map<String, Long> byHour = new LinkedHashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();

        logs.stream()
                .filter(log -> log.getTimestamp() != null)
                .filter(log -> spec.olderThan() ? log.getTimestamp().isBefore(cutoff) : !log.getTimestamp().isBefore(cutoff))
                .forEach(log -> {
                    String bucket = bucketKey(log.getTimestamp(), spec);
                    byHour.merge(bucket, 1L, Long::sum);
                    String type = eventTypeBucket(log);
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

    private String eventTypeBucket(AuditLog log) {
        if (isCustomEvent(log)) return "CUSTOM";
        return log.getCategory() != null ? log.getCategory() : "UNKNOWN";
    }

    private boolean isCustomEvent(AuditLog log) {
        if (log.getRawData() == null) return false;

        Object customEventType = log.getRawData().get("customEventType");
        if (customEventType != null && !String.valueOf(customEventType).isBlank()) {
            return true;
        }

        Object rawEventType = log.getRawData().get("eventType");
        if (rawEventType == null || String.valueOf(rawEventType).isBlank()) {
            return false;
        }

        String eventType = String.valueOf(rawEventType).trim().toUpperCase();
        return !BUILT_IN_EVENT_TYPES.contains(eventType);
    }

    private record RangeSpec(long amount, ChronoUnit unit, boolean olderThan) {}

    @SuppressWarnings("unchecked")
    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
