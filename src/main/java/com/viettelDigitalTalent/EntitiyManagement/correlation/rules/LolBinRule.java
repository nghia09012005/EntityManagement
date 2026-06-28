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
public class LolBinRule implements CorrelationRule {

    private static final List<String> LOL_BINS = List.of(
            "certutil.exe", "regsvr32.exe", "rundll32.exe", "mshta.exe",
            "wscript.exe", "cscript.exe", "bitsadmin.exe"
    );

    @Override
    public Optional<Incident> evaluate(List<AuditLog> events, LocalDateTime windowStart) {
        List<AuditLog> matched = events.stream()
                .filter(e -> e.getRawData() != null)
                .filter(e -> {
                    String proc = getString(e.getRawData(), "processName");
                    String cmd = getString(e.getRawData(), "commandLine");
                    if (proc == null || cmd == null) return false;
                    String procLow = proc.toLowerCase();
                    String cmdLow = cmd.toLowerCase();
                    return LOL_BINS.stream().anyMatch(procLow::contains)
                            && (cmdLow.contains("http://") || cmdLow.contains("https://")
                            || cmdLow.contains("-urlcache") || cmdLow.contains("scrobj"));
                })
                .collect(Collectors.toList());

        if (matched.isEmpty()) return Optional.empty();

        String proc = getString(matched.get(0).getRawData(), "processName");
        List<String> hosts = matched.stream()
                .map(e -> getString(e.getRawData(), "hostname"))
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Incident incident = new Incident();
        incident.setPatternName("LolBin");
        incident.setMitreId("T1218");
        incident.setTitle("Living-off-the-Land: " + proc + " tải payload từ mạng");
        incident.setSeverity("MEDIUM");
        incident.setWindowStart(windowStart);
        incident.setDetectedAt(LocalDateTime.now());
        incident.setUpdatedAt(LocalDateTime.now());
        incident.setRelatedEventIds(matched.stream().map(AuditLog::getEventId).collect(Collectors.toList()));
        incident.setAffectedEntities(Map.of("hosts", hosts));
        incident.setTimeline(buildTimeline(matched, e ->
                getString(e.getRawData(), "processName") + " | "
                        + truncate(getString(e.getRawData(), "commandLine"), 80)));
        incident.setRecommendedActions(List.of(
                "Phân tích payload được tải xuống",
                "Kiểm tra registry và scheduled tasks trên host bị ảnh hưởng",
                "Block URL nguồn tại proxy/firewall",
                "Quét toàn bộ host với AV/EDR"
        ));
        return Optional.of(incident);
    }
}