package com.viettelDigitalTalent.EntitiyManagement.correlation.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final AuditLogRepository auditLogRepository;

    private static final Set<String> VALID_STATUSES = Set.of("NEW", "INVESTIGATING", "RESOLVED");

    @GetMapping
    public ResponseEntity<Page<Incident>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(
                incidentRepository.findAllByTenantIdOrderByDetectedAtDesc(
                        tenantId(authentication), PageRequest.of(page, size)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Object> updateStatus(@PathVariable String id, @RequestParam String status) {
        if (!VALID_STATUSES.contains(status.toUpperCase())) {
            return ResponseEntity.badRequest().build();
        }
        return incidentRepository.findById(id).map(incident -> {
            incident.setStatus(status.toUpperCase());
            incident.setUpdatedAt(LocalDateTime.now());
            incidentRepository.save(incident);
            return ResponseEntity.<Void>ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(Authentication authentication) {
        String tenantId = tenantId(authentication);
        List<Incident> all = incidentRepository.findAllByTenantId(tenantId);

        Map<String, Long> bySeverity = all.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getSeverity() != null ? i.getSeverity() : "UNKNOWN",
                        Collectors.counting()));

        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getStatus() != null ? i.getStatus() : "UNKNOWN",
                        Collectors.counting()));

        // Top IPs from incidents (attacker sources)
        Map<String, Long> ipCount = new LinkedHashMap<>();
        Map<String, String> ipMaxSeverity = new HashMap<>();
        for (Incident inc : all) {
            if (inc.getAffectedEntities() == null) continue;
            List<String> ips = inc.getAffectedEntities().get("ips");
            if (ips == null) continue;
            for (String ip : ips) {
                ipCount.merge(ip, 1L, Long::sum);
                String cur = ipMaxSeverity.getOrDefault(ip, "LOW");
                if (severityRank(inc.getSeverity()) > severityRank(cur)) {
                    ipMaxSeverity.put(ip, inc.getSeverity());
                }
            }
        }
        List<Map<String, Object>> topIps = ipCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of(
                        "ip", e.getKey(),
                        "count", e.getValue(),
                        "severity", ipMaxSeverity.getOrDefault(e.getKey(), "LOW")))
                .collect(Collectors.toList());

        // Top hosts from recent audit logs (most active targets)
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<AuditLog> recent = auditLogRepository.findRecentEvents(since, tenantId);
        Map<String, Long> hostCount = new LinkedHashMap<>();
        for (AuditLog log : recent) {
            if (log.getRawData() == null) continue;
            Set<String> seen = new HashSet<>();
            for (String key : new String[]{"hostname", "targetHost", "workstation"}) {
                String h = getString(log.getRawData(), key);
                if (h != null && !h.isBlank() && seen.add(h)) {
                    hostCount.merge(h, 1L, Long::sum);
                }
            }
        }
        List<Map<String, Object>> topHosts = hostCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of("host", e.getKey(), "count", e.getValue()))
                .collect(Collectors.toList());

        long totalAlerts = auditLogRepository.countByCategory("THREAT", tenantId)
                + auditLogRepository.countByCategory("SECURITY_FINDING", tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalIncidents", all.size());
        result.put("bySeverity", bySeverity);
        result.put("byStatus", byStatus);
        result.put("topIps", topIps);
        result.put("topHosts", topHosts);
        result.put("totalAlerts", totalAlerts);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Page<AuditLog>> alerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(auditLogRepository.findAlerts(PageRequest.of(page, size), tenantId(authentication)));
    }

    @SuppressWarnings("unchecked")
    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }

    private int severityRank(String s) {
        return switch (s != null ? s : "") {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }
}