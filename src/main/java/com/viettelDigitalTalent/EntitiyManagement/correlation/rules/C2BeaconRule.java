package com.viettelDigitalTalent.EntitiyManagement.correlation.rules;

import com.viettelDigitalTalent.EntitiyManagement.correlation.CorrelationRule;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class C2BeaconRule implements CorrelationRule {

    private static final int MIN_CONNECTIONS = 3;

    @Override
    public Optional<Incident> evaluate(List<AuditLog> events, LocalDateTime windowStart) {
        Map<String, List<AuditLog>> byDomain = events.stream()
                .filter(e -> e.getRawData() != null && e.getRawData().containsKey("dstDomain"))
                .collect(Collectors.groupingBy(e -> String.valueOf(e.getRawData().get("dstDomain"))));

        for (Map.Entry<String, List<AuditLog>> entry : byDomain.entrySet()) {
            String domain = entry.getKey();
            if ("null".equals(domain) || domain.isBlank()) continue;

            List<AuditLog> domainEvents = entry.getValue();
            if (domainEvents.size() < MIN_CONNECTIONS) continue;

            List<String> srcIps = domainEvents.stream()
                    .map(e -> getString(e.getRawData(), "srcIp"))
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());

            Incident incident = new Incident();
            incident.setPatternName("C2Beacon");
            incident.setMitreId("T1071");
            incident.setTitle("C2 Beacon phát hiện đến " + domain + " (" + domainEvents.size() + " kết nối)");
            incident.setSeverity("HIGH");
            incident.setWindowStart(windowStart);
            incident.setDetectedAt(LocalDateTime.now());
            incident.setUpdatedAt(LocalDateTime.now());
            incident.setRelatedEventIds(domainEvents.stream().map(AuditLog::getEventId).collect(Collectors.toList()));
            incident.setAffectedEntities(Map.of("domains", List.of(domain), "ips", srcIps));
            incident.setTimeline(buildTimeline(domainEvents, e ->
                    getString(e.getRawData(), "srcIp") + " → "
                            + domain + ":" + getString(e.getRawData(), "dstPort")));
            incident.setRecommendedActions(List.of(
                    "Block domain " + domain + " tại DNS/proxy ngay lập tức",
                    "Kiểm tra tất cả host có kết nối đến domain này",
                    "Thu thập network capture từ các host bị ảnh hưởng",
                    "Phân tích process gây ra beacon (netstat -b, Sysmon Event 3)"
            ));
            return Optional.of(incident);
        }
        return Optional.empty();
    }
}