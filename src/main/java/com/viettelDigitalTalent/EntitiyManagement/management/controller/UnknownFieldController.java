package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.UnknownFieldDashboardDto;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.UnknownFieldDashboardDto.FieldStat;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.UnknownFieldEventPageDto;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.UnknownFieldOccurrence;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UnknownFieldOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unknown-fields")
@RequiredArgsConstructor
public class UnknownFieldController {

    private final UnknownFieldOccurrenceRepository occurrenceRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    public ResponseEntity<List<UnknownFieldDashboardDto>> stats(
            Authentication authentication,
            @RequestParam(defaultValue = "5") int top,
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(defaultValue = "") String fieldName) {
        int limit = Math.min(Math.max(top, 1), 20);
        String tenantId = tenantId(authentication);
        List<UnknownFieldOccurrence> occurrences = occurrenceRepository.findAll(tenantId);

        RangeSpec spec = resolveRange(range);
        LocalDateTime cutoff = LocalDateTime.now().minus(spec.amount(), spec.unit());

        String search = fieldName == null ? "" : fieldName.trim().toLowerCase();

        List<UnknownFieldOccurrence> filtered = occurrences.stream()
                .filter(o -> o.getOccurredAt() != null)
                .filter(o -> spec.olderThan() ? o.getOccurredAt().isBefore(cutoff) : !o.getOccurredAt().isBefore(cutoff))
                .filter(o -> search.isEmpty() || (o.getFieldName() != null && o.getFieldName().toLowerCase().contains(search)))
                .toList();

        List<String> eventTypes = filtered.stream()
                .map(UnknownFieldOccurrence::getEventType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<UnknownFieldDashboardDto> result = new ArrayList<>();
        for (String eventType : eventTypes) {
            Map<String, FieldAggregate> aggregates = new LinkedHashMap<>();
            filtered.stream()
                    .filter(o -> eventType.equals(o.getEventType()))
                    .forEach(o -> {
                        String occurrenceFieldName = o.getFieldName();
                        if (occurrenceFieldName == null || occurrenceFieldName.isBlank()) return;
                        FieldAggregate aggregate = aggregates.computeIfAbsent(occurrenceFieldName, key -> new FieldAggregate());
                        aggregate.count += 1;
                        if (o.getEventId() != null && !o.getEventId().isBlank()) {
                            aggregate.eventIds.add(o.getEventId());
                        }
                        if (aggregate.sampleValue == null && o.getSampleValue() != null) {
                            aggregate.sampleValue = o.getSampleValue();
                        }
                        boolean isNewer = o.getOccurredAt() != null && (aggregate.lastSeen == null || o.getOccurredAt().isAfter(aggregate.lastSeen));
                        if (isNewer) {
                            aggregate.lastSeen = o.getOccurredAt();
                            if (o.getEventId() != null && !o.getEventId().isBlank()) {
                                aggregate.lastEventId = o.getEventId();
                            }
                        } else if (aggregate.lastEventId == null && o.getEventId() != null && !o.getEventId().isBlank()) {
                            aggregate.lastEventId = o.getEventId();
                        }
                    });

                        List<Map.Entry<String, FieldAggregate>> topFieldEntries = aggregates.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue().count, a.getValue().count))
                    .limit(limit)
                    .toList();

                        List<FieldStat> topFields = topFieldEntries.stream()
                    .map(s -> new FieldStat(
                            s.getKey(),
                            s.getValue().count,
                            s.getValue().sampleValue,
                            s.getValue().lastSeen != null ? s.getValue().lastSeen.toString() : null,
                            s.getValue().lastEventId,
                            List.copyOf(s.getValue().eventIds)))
                    .toList();
            result.add(new UnknownFieldDashboardDto(eventType, topFields));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/events")
    public ResponseEntity<UnknownFieldEventPageDto> events(
            Authentication authentication,
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(defaultValue = "") String fieldName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        RangeSpec spec = resolveRange(range);
        LocalDateTime cutoff = LocalDateTime.now().minus(spec.amount(), spec.unit());
        String search = fieldName == null ? "" : fieldName.trim();
        String tenantId = tenantId(authentication);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 50);
        List<UnknownFieldOccurrence> pageItems = occurrenceRepository.findPage(tenantId, search, cutoff, safePage, safeSize);
        long totalItems = occurrenceRepository.count(tenantId, search, cutoff);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / safeSize);

        List<UnknownFieldEventPageDto.UnknownFieldEventDto> content = pageItems.stream()
                .map(o -> {
                    AuditLog log = o.getEventId() != null ? auditLogRepository.findByEventId(o.getEventId()) : null;
                    String rawPayload = log != null ? log.getRawEvent() : null;
                    return new UnknownFieldEventPageDto.UnknownFieldEventDto(
                            o.getEventId(),
                            o.getEventType(),
                            o.getFieldName(),
                            o.getSampleValue(),
                            rawPayload,
                            o.getOccurredAt() != null ? o.getOccurredAt().toString() : null
                    );
                })
                .toList();

        return ResponseEntity.ok(new UnknownFieldEventPageDto(content, totalItems, totalPages, safePage, safeSize));
    }

    private RangeSpec resolveRange(String range) {
        return switch (range == null ? "" : range.trim().toLowerCase()) {
            case "7d" -> new RangeSpec(7, ChronoUnit.DAYS, false);
            case "30d" -> new RangeSpec(30, ChronoUnit.DAYS, false);
            case "30d+" -> new RangeSpec(30, ChronoUnit.DAYS, true);
            default -> new RangeSpec(24, ChronoUnit.HOURS, false);
        };
    }

    private record RangeSpec(long amount, ChronoUnit unit, boolean olderThan) {}

    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }

    private static class FieldAggregate {
        long count;
        String sampleValue;
        LocalDateTime lastSeen;
        String lastEventId;
        java.util.Set<String> eventIds = new java.util.LinkedHashSet<>();
    }
}
