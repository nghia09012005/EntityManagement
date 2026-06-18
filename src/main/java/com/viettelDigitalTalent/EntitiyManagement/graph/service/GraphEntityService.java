package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphEntityService {

    private final Neo4jClient neo4jClient;

    public void save(BaseEvent event) {
        try {
            LocalDateTime now = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
            if (event instanceof AuthenticationEvent auth) saveAuth(auth, now);
            else if (event instanceof ProcessEvent proc)    saveProcess(proc, now);
            else if (event instanceof NetworkEvent net)     saveNetwork(net, now);
            else if (event instanceof AlertEvent alert)     saveAlert(alert, now);
        } catch (Exception e) {
            log.error("[Graph] Lỗi khi lưu entity cho event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    private void saveAuth(AuthenticationEvent e, LocalDateTime now) {
        if (e.getUsername() == null || e.getWorkstation() == null) return;

        Map<String, Object> p = new HashMap<>();
        p.put("username", e.getUsername());
        p.put("hostname", e.getWorkstation());
        p.put("now", now.toString());

        neo4jClient.query("""
                MERGE (u:User {username: $username})
                MERGE (h:Host {hostname: $hostname})
                MERGE (u)-[r:LOGGED_IN_TO]->(h)
                ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1
                ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                """).bindAll(p).run();

        if (e.getIpAddress() != null && !e.getIpAddress().isBlank()) {
            GeoInfo geo = (GeoInfo) e.getRawData().get("geo");
            Map<String, Object> ip = new HashMap<>();
            ip.put("address", e.getIpAddress());
            ip.put("country", geo != null ? geo.getCountry() : null);
            ip.put("asn",     geo != null ? geo.getAsn()     : null);
            ip.put("hostname", e.getWorkstation());
            ip.put("now", now.toString());

            neo4jClient.query("""
                    MERGE (ip:IP {address: $address})
                    ON CREATE SET ip.country = $country, ip.asn = $asn
                    ON MATCH SET  ip.country = coalesce($country, ip.country),
                                  ip.asn     = coalesce($asn,     ip.asn)
                    WITH ip
                    MATCH (h:Host {hostname: $hostname})
                    MERGE (ip)-[r:AUTHENTICATED_TO]->(h)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                    """).bindAll(ip).run();
        }
    }

    private void saveProcess(ProcessEvent e, LocalDateTime now) {
        if (e.getFileHash() == null) return;

        MalwareInfo mal = (MalwareInfo) e.getRawData().get("malware");
        String hash = e.getFileHash().trim().toLowerCase();

        Map<String, Object> p = new HashMap<>();
        p.put("hash",      hash);
        p.put("verdict",   mal != null ? mal.getVerdict() : "UNKNOWN");
        p.put("malicious", mal != null && mal.isMalicious());
        p.put("family",    mal != null ? mal.getFamily()  : null);
        p.put("now",       now.toString());

        neo4jClient.query("""
                MERGE (f:FileHash {hash: $hash})
                ON CREATE SET f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                ON MATCH SET  f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                """).bindAll(p).run();

        // Chỉ tạo quan hệ EXECUTED_ON khi có hostname thật
        Object rawHostname = e.getRawData().get("hostname");
        if (rawHostname instanceof String hostname && !hostname.isBlank()) {
            p.put("hostname",    hostname);
            p.put("processName", e.getProcessName());

            neo4jClient.query("""
                    MATCH (f:FileHash {hash: $hash})
                    MERGE (h:Host {hostname: $hostname})
                    MERGE (f)-[r:EXECUTED_ON]->(h)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1, r.processName = $processName
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                    """).bindAll(p).run();
        }
    }

    private void saveNetwork(NetworkEvent e, LocalDateTime now) {
        if (e.getSrcIp() == null || e.getDstIp() == null) return;

        GeoInfo srcGeo = (GeoInfo) e.getRawData().get("srcGeo");
        GeoInfo dstGeo = (GeoInfo) e.getRawData().get("dstGeo");

        Map<String, Object> p = new HashMap<>();
        p.put("srcAddress", e.getSrcIp());
        p.put("srcCountry", srcGeo != null ? srcGeo.getCountry() : null);
        p.put("srcAsn",     srcGeo != null ? srcGeo.getAsn()     : null);
        p.put("dstAddress", e.getDstIp());
        p.put("dstCountry", dstGeo != null ? dstGeo.getCountry() : null);
        p.put("dstAsn",     dstGeo != null ? dstGeo.getAsn()     : null);
        p.put("dstPort",    e.getDstPort());
        p.put("now",        now.toString());

        neo4jClient.query("""
                MERGE (src:IP {address: $srcAddress})
                ON CREATE SET src.country = $srcCountry, src.asn = $srcAsn
                ON MATCH SET  src.country = coalesce($srcCountry, src.country),
                              src.asn     = coalesce($srcAsn,     src.asn)
                MERGE (dst:IP {address: $dstAddress})
                ON CREATE SET dst.country = $dstCountry, dst.asn = $dstAsn
                ON MATCH SET  dst.country = coalesce($dstCountry, dst.country),
                              dst.asn     = coalesce($dstAsn,     dst.asn)
                MERGE (src)-[r:CONNECTED_TO]->(dst)
                ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1, r.dstPort = $dstPort
                ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                """).bindAll(p).run();

        if (e.getDstDomain() != null && !e.getDstDomain().isBlank()) {
            Map<String, Object> dp = new HashMap<>();
            dp.put("address", e.getDstIp());
            dp.put("domain",  e.getDstDomain());
            dp.put("now",     now.toString());

            neo4jClient.query("""
                    MATCH (ip:IP {address: $address})
                    MERGE (d:Domain {name: $domain})
                    MERGE (ip)-[r:RESOLVES_TO]->(d)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                    """).bindAll(dp).run();
        }
    }

    private void saveAlert(AlertEvent e, LocalDateTime now) {
        if (e.getTargetUser() != null && e.getTargetIp() != null) {
            GeoInfo geo = (GeoInfo) e.getRawData().get("geo");
            Map<String, Object> p = new HashMap<>();
            p.put("username",  e.getTargetUser());
            p.put("address",   e.getTargetIp());
            p.put("country",   geo != null ? geo.getCountry() : null);
            p.put("asn",       geo != null ? geo.getAsn()     : null);
            p.put("alertName", e.getAlertName());
            p.put("severity",  e.getSeverity());
            p.put("now",       now.toString());

            neo4jClient.query("""
                    MERGE (u:User {username: $username})
                    MERGE (ip:IP {address: $address})
                    ON CREATE SET ip.country = $country, ip.asn = $asn
                    ON MATCH SET  ip.country = coalesce($country, ip.country),
                                  ip.asn     = coalesce($asn,     ip.asn)
                    MERGE (u)-[r:ALERTED_FROM]->(ip)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.alertName = $alertName, r.severity = $severity
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                    """).bindAll(p).run();
        }

        if (e.getTargetFileHash() != null && e.getTargetIp() != null) {
            MalwareInfo mal = (MalwareInfo) e.getRawData().get("malware");
            Map<String, Object> p = new HashMap<>();
            p.put("hash",      e.getTargetFileHash().trim().toLowerCase());
            p.put("verdict",   mal != null ? mal.getVerdict() : "UNKNOWN");
            p.put("malicious", mal != null && mal.isMalicious());
            p.put("family",    mal != null ? mal.getFamily()  : null);
            p.put("address",   e.getTargetIp());
            p.put("now",       now.toString());

            neo4jClient.query("""
                    MERGE (f:FileHash {hash: $hash})
                    ON CREATE SET f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                    ON MATCH SET  f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                    MERGE (ip:IP {address: $address})
                    MERGE (f)-[r:DETECTED_ON]->(ip)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1
                    """).bindAll(p).run();
        }
    }
}
