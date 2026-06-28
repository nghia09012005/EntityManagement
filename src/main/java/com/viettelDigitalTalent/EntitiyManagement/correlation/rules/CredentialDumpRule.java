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
public class CredentialDumpRule implements CorrelationRule {

    private static final List<String> KEYWORDS = List.of(
            "mimikatz", "lsadump", "sekurlsa", "logonpasswords", "wce.exe", "hashdump", "lsass"
    );

    @Override
    public Optional<Incident> evaluate(List<AuditLog> events, LocalDateTime windowStart) {
        List<AuditLog> matched = events.stream()
                .filter(e -> e.getRawData() != null && e.getRawData().containsKey("commandLine"))
                .filter(e -> {
                    String cmd = String.valueOf(e.getRawData().get("commandLine")).toLowerCase();
                    return KEYWORDS.stream().anyMatch(cmd::contains);
                })
                .collect(Collectors.toList());

        if (matched.isEmpty()) return Optional.empty();

        String host = getString(matched.get(0).getRawData(), "hostname");
        List<String> hosts = matched.stream()
                .map(e -> getString(e.getRawData(), "hostname"))
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Incident incident = new Incident();
        incident.setPatternName("CredentialDump");
        incident.setMitreId("T1003");
        incident.setTitle("Credential Dumping phát hiện trên " + (host != null ? host : "unknown"));
        incident.setSeverity("CRITICAL");
        incident.setWindowStart(windowStart);
        incident.setDetectedAt(LocalDateTime.now());
        incident.setUpdatedAt(LocalDateTime.now());
        incident.setRelatedEventIds(matched.stream().map(AuditLog::getEventId).collect(Collectors.toList()));
        incident.setAffectedEntities(Map.of("hosts", hosts));
        incident.setTimeline(buildTimeline(matched, e ->
                getString(e.getRawData(), "processName") + " | "
                        + truncate(getString(e.getRawData(), "commandLine"), 80)));
        incident.setRecommendedActions(List.of(
                "Isolate host " + (host != null ? host : "bị ảnh hưởng") + " khỏi network ngay",
                "Thu thập memory dump và forensic artifacts",
                "Force reset tất cả credentials có thể đã bị lộ",
                "Quét toàn bộ host tìm dấu hiệu persistence",
                "Escalate lên IR team ngay lập tức"
        ));
        return Optional.of(incident);
    }
}