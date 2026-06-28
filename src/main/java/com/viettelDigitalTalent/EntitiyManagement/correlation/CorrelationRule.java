package com.viettelDigitalTalent.EntitiyManagement.correlation;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface CorrelationRule {

    Optional<Incident> evaluate(List<AuditLog> events, LocalDateTime windowStart);

    default String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    default String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    default List<Map<String, String>> buildTimeline(List<AuditLog> events,
                                                     java.util.function.Function<AuditLog, String> summaryFn) {
        return events.stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("time", e.getTimestamp() != null ? e.getTimestamp().toString() : "");
                    entry.put("summary", summaryFn.apply(e));
                    entry.put("eventId", e.getEventId() != null ? e.getEventId() : "");
                    return entry;
                })
                .collect(Collectors.toList());
    }
}