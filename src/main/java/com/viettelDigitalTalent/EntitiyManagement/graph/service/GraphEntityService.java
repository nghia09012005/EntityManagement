package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.IpIntelInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GraphEntityService {

    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;
    private final Counter authCounter;
    private final Counter processCounter;
    private final Counter networkCounter;
    private final Counter alertCounter;

    public GraphEntityService(Neo4jClient neo4jClient, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.neo4jClient  = neo4jClient;
        this.objectMapper = objectMapper;
        this.authCounter    = Counter.builder("soc.entity.saved").tag("event_type", "AUTHENTICATION").register(meterRegistry);
        this.processCounter = Counter.builder("soc.entity.saved").tag("event_type", "PROCESS").register(meterRegistry);
        this.networkCounter = Counter.builder("soc.entity.saved").tag("event_type", "NETWORK").register(meterRegistry);
        this.alertCounter   = Counter.builder("soc.entity.saved").tag("event_type", "ALERT").register(meterRegistry);
    }

    public void save(BaseEvent event) {
        try {
            LocalDateTime now = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
            String eid = event.getEventId();
            if (event instanceof AuthenticationEvent auth) { saveAuth(auth, now, eid);    authCounter.increment(); }
            else if (event instanceof ProcessEvent proc)   { saveProcess(proc, now, eid); processCounter.increment(); }
            else if (event instanceof NetworkEvent net)    { saveNetwork(net, now, eid);  networkCounter.increment(); }
            else if (event instanceof AlertEvent alert)    { saveAlert(alert, now, eid);  alertCounter.increment(); }
        } catch (Exception e) {
            log.error("[Graph] Lỗi khi lưu entity cho event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** An toàn với cả in-process object lẫn LinkedHashMap sau Kafka JSON round-trip. */
    private <T> T fromRawData(Map<String, Object> rawData, String key, Class<T> type) {
        Object val = rawData.get(key);
        if (val == null) return null;
        if (type.isInstance(val)) return type.cast(val);
        return objectMapper.convertValue(val, type);
    }

    /** Populate IP enrichment fields từ GeoInfo + IpIntelInfo vào params map. */
    private void putIpEnrichment(Map<String, Object> p, GeoInfo geo, IpIntelInfo intel) {
        p.put("country",     geo   != null ? geo.getCountry()     : null);
        p.put("city",        geo   != null ? geo.getCity()        : null);
        p.put("asn",         geo   != null ? geo.getAsn()         : null);
        p.put("abuseScore",  intel != null ? intel.getAbuseScore()  : 0);
        p.put("threatLevel", intel != null ? intel.getThreatLevel() : null);
        p.put("isMalicious", intel != null && intel.isMalicious());
    }

    // ── Save methods ──────────────────────────────────────────────────────────

    private void saveAuth(AuthenticationEvent e, LocalDateTime now, String eventId) {
        String username = EntityNormalizer.username(e.getUsername());
        String hostname = EntityNormalizer.hostname(e.getWorkstation());
        if (username == null || hostname == null) return;

        Map<String, Object> p = new HashMap<>();
        p.put("username", username);
        p.put("hostname", hostname);
        p.put("now",      now.toString());
        p.put("eventId",  eventId);

        neo4jClient.query("""
                MERGE (u:User {username: $username})
                MERGE (h:Host {hostname: $hostname})
                MERGE (u)-[r:LOGGED_IN_TO]->(h)
                ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                              r.firstEventId = $eventId, r.lastEventId = $eventId
                ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                              r.lastEventId = $eventId
                """).bindAll(p).run();

        String ipAddr = EntityNormalizer.ip(e.getIpAddress());
        if (ipAddr != null) {
            GeoInfo    geo   = fromRawData(e.getRawData(), "geo",     GeoInfo.class);
            IpIntelInfo intel = fromRawData(e.getRawData(), "ipIntel", IpIntelInfo.class);

            Map<String, Object> ip = new HashMap<>();
            ip.put("address",  ipAddr);
            ip.put("hostname", hostname);
            ip.put("now",      now.toString());
            ip.put("eventId",  eventId);
            putIpEnrichment(ip, geo, intel);

            neo4jClient.query("""
                    MERGE (ip:IP {address: $address})
                    ON CREATE SET ip.country = $country, ip.city = $city, ip.asn = $asn,
                                  ip.abuseScore = $abuseScore, ip.threatLevel = $threatLevel,
                                  ip.isMalicious = $isMalicious
                    ON MATCH SET  ip.country    = coalesce($country,     ip.country),
                                  ip.city       = coalesce($city,        ip.city),
                                  ip.asn        = coalesce($asn,         ip.asn),
                                  ip.abuseScore  = $abuseScore,
                                  ip.threatLevel = coalesce($threatLevel, ip.threatLevel),
                                  ip.isMalicious = $isMalicious
                    WITH ip
                    MATCH (h:Host {hostname: $hostname})
                    MERGE (ip)-[r:AUTHENTICATED_TO]->(h)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(ip).run();
        }
    }

    private void saveProcess(ProcessEvent e, LocalDateTime now, String eventId) {
        String hash = EntityNormalizer.hash(e.getFileHash());
        if (hash == null) return;

        MalwareInfo mal = fromRawData(e.getRawData(), "malware", MalwareInfo.class);

        Map<String, Object> p = new HashMap<>();
        p.put("hash",      hash);
        p.put("verdict",   mal != null ? mal.getVerdict() : "UNKNOWN");
        p.put("malicious", mal != null && mal.isMalicious());
        p.put("family",    mal != null ? mal.getFamily()  : null);
        p.put("now",       now.toString());
        p.put("eventId",   eventId);

        neo4jClient.query("""
                MERGE (f:FileHash {hash: $hash})
                ON CREATE SET f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                ON MATCH SET  f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                """).bindAll(p).run();

        Object rawHostname = e.getRawData().get("hostname");
        if (rawHostname instanceof String raw) {
            String hostname = EntityNormalizer.hostname(raw);
            if (hostname != null) {
                p.put("hostname",    hostname);
                p.put("processName", e.getProcessName());

                neo4jClient.query("""
                        MATCH (f:FileHash {hash: $hash})
                        MERGE (h:Host {hostname: $hostname})
                        MERGE (f)-[r:EXECUTED_ON]->(h)
                        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                      r.processName = $processName,
                                      r.firstEventId = $eventId, r.lastEventId = $eventId
                        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                      r.lastEventId = $eventId
                        """).bindAll(p).run();
            }
        }
    }

    private void saveNetwork(NetworkEvent e, LocalDateTime now, String eventId) {
        String srcIp = EntityNormalizer.ip(e.getSrcIp());
        String dstIp = EntityNormalizer.ip(e.getDstIp());
        if (srcIp == null || dstIp == null) return;

        GeoInfo     srcGeo   = fromRawData(e.getRawData(), "srcGeo",    GeoInfo.class);
        GeoInfo     dstGeo   = fromRawData(e.getRawData(), "dstGeo",    GeoInfo.class);
        IpIntelInfo srcIntel = fromRawData(e.getRawData(), "srcIpIntel", IpIntelInfo.class);
        IpIntelInfo dstIntel = fromRawData(e.getRawData(), "dstIpIntel", IpIntelInfo.class);

        Map<String, Object> p = new HashMap<>();
        p.put("srcAddress",    srcIp);
        p.put("srcCountry",    srcGeo   != null ? srcGeo.getCountry()      : null);
        p.put("srcCity",       srcGeo   != null ? srcGeo.getCity()         : null);
        p.put("srcAsn",        srcGeo   != null ? srcGeo.getAsn()          : null);
        p.put("srcAbuseScore", srcIntel != null ? srcIntel.getAbuseScore() : 0);
        p.put("srcThreatLevel",srcIntel != null ? srcIntel.getThreatLevel(): null);
        p.put("srcIsMalicious",srcIntel != null && srcIntel.isMalicious());
        p.put("dstAddress",    dstIp);
        p.put("dstCountry",    dstGeo   != null ? dstGeo.getCountry()      : null);
        p.put("dstCity",       dstGeo   != null ? dstGeo.getCity()         : null);
        p.put("dstAsn",        dstGeo   != null ? dstGeo.getAsn()          : null);
        p.put("dstAbuseScore", dstIntel != null ? dstIntel.getAbuseScore() : 0);
        p.put("dstThreatLevel",dstIntel != null ? dstIntel.getThreatLevel(): null);
        p.put("dstIsMalicious",dstIntel != null && dstIntel.isMalicious());
        p.put("dstPort",       e.getDstPort());
        p.put("now",           now.toString());
        p.put("eventId",       eventId);

        neo4jClient.query("""
                MERGE (src:IP {address: $srcAddress})
                ON CREATE SET src.country = $srcCountry, src.city = $srcCity, src.asn = $srcAsn,
                              src.abuseScore = $srcAbuseScore, src.threatLevel = $srcThreatLevel,
                              src.isMalicious = $srcIsMalicious
                ON MATCH SET  src.country    = coalesce($srcCountry,     src.country),
                              src.city       = coalesce($srcCity,        src.city),
                              src.asn        = coalesce($srcAsn,         src.asn),
                              src.abuseScore  = $srcAbuseScore,
                              src.threatLevel = coalesce($srcThreatLevel, src.threatLevel),
                              src.isMalicious = $srcIsMalicious
                MERGE (dst:IP {address: $dstAddress})
                ON CREATE SET dst.country = $dstCountry, dst.city = $dstCity, dst.asn = $dstAsn,
                              dst.abuseScore = $dstAbuseScore, dst.threatLevel = $dstThreatLevel,
                              dst.isMalicious = $dstIsMalicious
                ON MATCH SET  dst.country    = coalesce($dstCountry,     dst.country),
                              dst.city       = coalesce($dstCity,        dst.city),
                              dst.asn        = coalesce($dstAsn,         dst.asn),
                              dst.abuseScore  = $dstAbuseScore,
                              dst.threatLevel = coalesce($dstThreatLevel, dst.threatLevel),
                              dst.isMalicious = $dstIsMalicious
                MERGE (src)-[r:CONNECTED_TO]->(dst)
                ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                              r.dstPort = $dstPort,
                              r.firstEventId = $eventId, r.lastEventId = $eventId
                ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                              r.lastEventId = $eventId
                """).bindAll(p).run();

        String dstDomain = EntityNormalizer.domain(e.getDstDomain());
        if (dstDomain != null) {
            Map<String, Object> dp = new HashMap<>();
            dp.put("address", dstIp);
            dp.put("domain",  dstDomain);
            dp.put("now",     now.toString());
            dp.put("eventId", eventId);

            neo4jClient.query("""
                    MATCH (ip:IP {address: $address})
                    MERGE (d:Domain {name: $domain})
                    MERGE (ip)-[r:RESOLVES_TO]->(d)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(dp).run();
        }
    }

    private void saveAlert(AlertEvent e, LocalDateTime now, String eventId) {
        String targetUser     = EntityNormalizer.username(e.getTargetUser());
        String targetIp       = EntityNormalizer.ip(e.getTargetIp());
        String targetHost     = EntityNormalizer.hostname(e.getTargetHost());
        String targetDomain   = EntityNormalizer.domain(e.getTargetDomain());
        String targetFileHash = EntityNormalizer.hash(e.getTargetFileHash());

        if (targetUser != null && targetIp != null) {
            GeoInfo     geo   = fromRawData(e.getRawData(), "geo",     GeoInfo.class);
            IpIntelInfo intel = fromRawData(e.getRawData(), "ipIntel", IpIntelInfo.class);

            Map<String, Object> p = new HashMap<>();
            p.put("username",  targetUser);
            p.put("address",   targetIp);
            p.put("alertName", e.getAlertName());
            p.put("severity",  e.getSeverity());
            p.put("now",       now.toString());
            p.put("eventId",   eventId);
            putIpEnrichment(p, geo, intel);

            neo4jClient.query("""
                    MERGE (u:User {username: $username})
                    MERGE (ip:IP {address: $address})
                    ON CREATE SET ip.country = $country, ip.city = $city, ip.asn = $asn,
                                  ip.abuseScore = $abuseScore, ip.threatLevel = $threatLevel,
                                  ip.isMalicious = $isMalicious
                    ON MATCH SET  ip.country    = coalesce($country,     ip.country),
                                  ip.city       = coalesce($city,        ip.city),
                                  ip.asn        = coalesce($asn,         ip.asn),
                                  ip.abuseScore  = $abuseScore,
                                  ip.threatLevel = coalesce($threatLevel, ip.threatLevel),
                                  ip.isMalicious = $isMalicious
                    MERGE (u)-[r:ALERTED_FROM]->(ip)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.alertName = $alertName, r.severity = $severity,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(p).run();
        }

        if (targetFileHash != null && targetIp != null) {
            MalwareInfo mal = fromRawData(e.getRawData(), "malware", MalwareInfo.class);
            Map<String, Object> p = new HashMap<>();
            p.put("hash",      targetFileHash);
            p.put("verdict",   mal != null ? mal.getVerdict() : "UNKNOWN");
            p.put("malicious", mal != null && mal.isMalicious());
            p.put("family",    mal != null ? mal.getFamily()  : null);
            p.put("address",   targetIp);
            p.put("now",       now.toString());
            p.put("eventId",   eventId);

            neo4jClient.query("""
                    MERGE (f:FileHash {hash: $hash})
                    ON CREATE SET f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                    ON MATCH SET  f.verdict = $verdict, f.malicious = $malicious, f.family = $family
                    MERGE (ip:IP {address: $address})
                    MERGE (f)-[r:DETECTED_ON]->(ip)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(p).run();
        }

        if (targetHost != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("hostname",  targetHost);
            p.put("alertName", e.getAlertName());
            p.put("severity",  e.getSeverity());
            p.put("now",       now.toString());
            p.put("eventId",   eventId);

            neo4jClient.query("MERGE (h:Host {hostname: $hostname})").bindAll(p).run();

            if (targetIp != null) {
                p.put("address", targetIp);
                neo4jClient.query("""
                        MATCH (h:Host {hostname: $hostname})
                        MERGE (ip:IP {address: $address})
                        MERGE (ip)-[r:TARGETED_AT]->(h)
                        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                      r.alertName = $alertName, r.severity = $severity,
                                      r.firstEventId = $eventId, r.lastEventId = $eventId
                        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                      r.lastEventId = $eventId
                        """).bindAll(p).run();
            }
        }

        if (targetDomain != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("domain",    targetDomain);
            p.put("alertName", e.getAlertName());
            p.put("severity",  e.getSeverity());
            p.put("now",       now.toString());
            p.put("eventId",   eventId);

            neo4jClient.query("MERGE (d:Domain {name: $domain})").bindAll(p).run();

            if (targetIp != null) {
                p.put("address", targetIp);
                neo4jClient.query("""
                        MATCH (d:Domain {name: $domain})
                        MERGE (ip:IP {address: $address})
                        MERGE (ip)-[r:RESOLVES_TO]->(d)
                        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                      r.firstEventId = $eventId, r.lastEventId = $eventId
                        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                      r.lastEventId = $eventId
                        """).bindAll(p).run();
            }
        }
    }
}
