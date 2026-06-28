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
public class BruteForceRule implements CorrelationRule {

    @Override
    public Optional<Incident> evaluate(List<AuditLog> events, LocalDateTime windowStart) {
        Map<String, List<AuditLog>> byIp = events.stream()
                .filter(e -> e.getRawData() != null && e.getRawData().containsKey("ipAddress"))
                .collect(Collectors.groupingBy(e -> String.valueOf(e.getRawData().get("ipAddress"))));

        for (Map.Entry<String, List<AuditLog>> entry : byIp.entrySet()) {
            String ip = entry.getKey();
            if ("null".equals(ip) || ip.isBlank()) continue;

            List<AuditLog> ipEvents = entry.getValue();
            long failedCount = ipEvents.stream().filter(this::isFailed).count();
            boolean hasSuccess = ipEvents.stream().anyMatch(this::isSuccess);

            if (failedCount >= 3 && hasSuccess) {
                List<String> users = ipEvents.stream()
                        .map(e -> getString(e.getRawData(), "username"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                Incident incident = new Incident();
                incident.setPatternName("BruteForce");
                incident.setMitreId("T1110");
                incident.setTitle("Brute Force → Account Compromise từ " + ip);
                incident.setSeverity("HIGH");
                incident.setWindowStart(windowStart);
                incident.setDetectedAt(LocalDateTime.now());
                incident.setUpdatedAt(LocalDateTime.now());
                incident.setRelatedEventIds(ipEvents.stream().map(AuditLog::getEventId).collect(Collectors.toList()));
                incident.setAffectedEntities(Map.of("ips", List.of(ip), "users", users));
                incident.setTimeline(buildTimeline(ipEvents, e -> {
                    String success = getString(e.getRawData(), "success");
                    return "Auth " + ("true".equals(success) ? "SUCCESS" : "FAILED")
                            + " | user=" + getString(e.getRawData(), "username")
                            + " | ip=" + ip;
                }));
                incident.setRecommendedActions(List.of(
                        "Block IP " + ip + " tại firewall/proxy",
                        "Force reset mật khẩu cho: " + String.join(", ", users),
                        "Kiểm tra tất cả session đang active của user bị ảnh hưởng",
                        "Escalate lên IR team nếu phát hiện thêm lateral movement"
                ));
                return Optional.of(incident);
            }
        }
        return Optional.empty();
    }

    private boolean isFailed(AuditLog e) {
        Object s = e.getRawData().get("success");
        if (s instanceof Boolean) return !(Boolean) s;
        return "false".equalsIgnoreCase(String.valueOf(s));
    }

    private boolean isSuccess(AuditLog e) {
        Object s = e.getRawData().get("success");
        if (s instanceof Boolean) return (Boolean) s;
        return "true".equalsIgnoreCase(String.valueOf(s));
    }
}