package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class KnownEventFields {

    private KnownEventFields() {}

    private static final Set<String> BASE = Set.of(
            "class_uid", "category_uid", "activity_id", "severity_id", "time",
            "uid", "eventId", "event_id", "severity", "message", "status", "status_id",
            "tenantId", "source", "enrichment_id", "is_enriched", "raw_data",
            "eventType", "category", "unknown_fields", "enriched"
    );

    private static final Set<String> AUTHENTICATION = Set.of(
            "actor", "src_endpoint", "dst_endpoint",
            "username", "ipAddress", "workstation", "success"
    );

    private static final Set<String> PROCESS = Set.of(
            "process", "processName", "processPath", "fileHash", "commandLine", "hostname"
    );

    private static final Set<String> NETWORK = Set.of(
            "src_endpoint", "dst_endpoint", "srcIp", "dstIp", "dstDomain", "dstPort"
    );

    private static final Set<String> THREAT = Set.of(
            "finding", "actor", "dst_endpoint", "src_endpoint", "process",
            "target_url", "cloud_resource_id", "target_email", "cve_uid", "targetCve",
            "alertName", "severity", "description", "targetIp", "targetUser", "targetHost",
            "targetDomain", "targetFileHash", "targetProcess", "targetUrl",
            "targetCloudResourceId", "targetEmail", "sourceIp", "sourceHost", "sourceDomain"
    );

    private static final Map<String, Set<String>> BY_EVENT_TYPE = Map.of(
            "AUTHENTICATION", union(BASE, AUTHENTICATION),
            "PROCESS",        union(BASE, PROCESS),
            "NETWORK",        union(BASE, NETWORK),
            "THREAT",         union(BASE, THREAT)
    );

    public static Set<String> forEventType(String eventType) {
        if (eventType == null) return BASE;
        return BY_EVENT_TYPE.getOrDefault(eventType.toUpperCase(), BASE);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> merged = new HashSet<>(a);
        merged.addAll(b);
        return Set.copyOf(merged);
    }
}
