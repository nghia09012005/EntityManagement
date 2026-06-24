package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final DedupSignal dedupSignal;
    private final MeterRegistry meterRegistry;
    private final Counter authCounter;
    private final Counter processCounter;
    private final Counter networkCounter;
    private final Counter alertCounter;
    private final Timer authTimer;
    private final Timer processTimer;
    private final Timer networkTimer;
    private final Timer alertTimer;

    public GraphEntityService(Neo4jClient neo4jClient, MeterRegistry meterRegistry,
                              DedupSignal dedupSignal) {
        this.neo4jClient   = neo4jClient;
        this.dedupSignal   = dedupSignal;
        this.meterRegistry = meterRegistry;
        this.authCounter    = Counter.builder("soc.entity.saved").tag("event_type", "AUTHENTICATION").register(meterRegistry);
        this.processCounter = Counter.builder("soc.entity.saved").tag("event_type", "PROCESS").register(meterRegistry);
        this.networkCounter = Counter.builder("soc.entity.saved").tag("event_type", "NETWORK").register(meterRegistry);
        this.alertCounter   = Counter.builder("soc.entity.saved").tag("event_type", "ALERT").register(meterRegistry);
        this.authTimer    = Timer.builder("soc.neo4j.save.duration").tag("event_type", "AUTHENTICATION").publishPercentileHistogram(true).register(meterRegistry);
        this.processTimer = Timer.builder("soc.neo4j.save.duration").tag("event_type", "PROCESS").publishPercentileHistogram(true).register(meterRegistry);
        this.networkTimer = Timer.builder("soc.neo4j.save.duration").tag("event_type", "NETWORK").publishPercentileHistogram(true).register(meterRegistry);
        this.alertTimer   = Timer.builder("soc.neo4j.save.duration").tag("event_type", "ALERT").publishPercentileHistogram(true).register(meterRegistry);
    }

    private static final String REL_UPSERT = """
            ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                          r.firstEventId = $eventId, r.lastEventId = $eventId
            ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                          r.lastEventId = $eventId
            """;

    /** Chuyển tenantId (UUID) thành Neo4j label an toàn: T_abc_123 */
    static String tenantLabel(String tenantId) {
        // if (tenantId == null || tenantId.isBlank()) return "T_default";
        // return "T_" + tenantId.replace("-", "_");
        return tenantId;
    }

    public void save(BaseEvent event) {
        try {
            LocalDateTime now = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();
            String eid = event.getEventId();
            String tl  = tenantLabel(event.getTenantId());
            Timer.Sample neo4jSample = Timer.start(meterRegistry);
            Timer neo4jTimer;
            if (event instanceof AuthenticationEvent auth) { saveAuth(auth, now, eid, tl);    authCounter.increment();    dedupSignal.mark(); neo4jTimer = authTimer; }
            else if (event instanceof ProcessEvent proc)   { saveProcess(proc, now, eid, tl); processCounter.increment(); dedupSignal.mark(); neo4jTimer = processTimer; }
            else if (event instanceof NetworkEvent net)    { saveNetwork(net, now, eid, tl);  networkCounter.increment(); dedupSignal.mark(); neo4jTimer = networkTimer; }
            else if (event instanceof AlertEvent alert)    { saveAlert(alert, now, eid, tl);  alertCounter.increment();   dedupSignal.mark(); neo4jTimer = alertTimer; }
            else { neo4jTimer = authTimer; }
            neo4jSample.stop(neo4jTimer);
        } catch (Exception e) {
            log.error("[Graph] Lỗi khi lưu entity cho event {}: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    // ── Save methods ──────────────────────────────────────────────────────────

    private void saveAuth(AuthenticationEvent e, LocalDateTime now, String eventId, String tl) {
        String username = EntityNormalizer.username(e.getUsername());
        String hostname = EntityNormalizer.hostname(e.getWorkstation());
        if (username == null || hostname == null) return;

        Map<String, Object> p = new HashMap<>();
        p.put("username", username);
        p.put("hostname", hostname);
        p.put("now",      now.toString());
        p.put("eventId",  eventId);
        p.put("tenantId", tl);

        neo4jClient.query("""
        MERGE (u:User {tenantId: $tenantId, username: $username})
        MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})
        MERGE (u)-[r:LOGGED_IN_TO]->(h)
        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                      r.firstEventId = $eventId, r.lastEventId = $eventId
        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                      r.lastEventId = $eventId
        """).bindAll(p).run();

        String ipAddr = EntityNormalizer.ip(e.getIpAddress());
        if (ipAddr != null) {
            Map<String, Object> ip = new HashMap<>();
            ip.put("address",  ipAddr);
            ip.put("hostname", hostname);
            ip.put("now",      now.toString());
            ip.put("eventId",  eventId);
            ip.put("tenantId", tl);

            neo4jClient.query("""
                    MERGE (ip:IP {tenantId: $tenantId, address: $address})
                    WITH ip
                    MATCH (h:Host {tenantId: $tenantId, hostname: $hostname})
                    MERGE (ip)-[r:AUTHENTICATED_TO]->(h)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(ip).run();
        }
    }

    private void saveProcess(ProcessEvent e, LocalDateTime now, String eventId, String tl) {
        String hash = EntityNormalizer.hash(e.getFileHash());
        if (hash == null) return;

        Map<String, Object> p = new HashMap<>();
        p.put("hash",    hash);
        p.put("now",     now.toString());
        p.put("eventId", eventId);
        p.put("tenantId", tl);

        neo4jClient.query("MERGE (f:FileHash {tenantId: $tenantId,hash: $hash})").bindAll(p).run();

        String procName = EntityNormalizer.processName(e.getProcessName());
        if (procName != null) {
            Map<String, Object> pp = new HashMap<>();
            pp.put("name",        procName);
            pp.put("path",        e.getProcessPath());
            pp.put("commandLine", e.getCommandLine());
            pp.put("now",         now.toString());
            pp.put("eventId",     eventId);
            pp.put("tenantId", tl);
            neo4jClient.query("""
                    MERGE (proc:Process{tenantId: $tenantId, name: $name})
                    ON CREATE SET proc.path = $path, proc.commandLine = $commandLine
                    ON MATCH SET  proc.path = coalesce($path, proc.path),
                                  proc.commandLine = coalesce($commandLine, proc.commandLine)
                    """).bindAll(pp).run();

            pp.put("hash", hash);
            neo4jClient.query("""
        MATCH (f:FileHash {tenantId: $tenantId, hash: $hash})
        MATCH (proc:Process {tenantId: $tenantId, name: $name})
        MERGE (f)-[r:HASH_OF]->(proc)
        """ + REL_UPSERT).bindAll(pp).run();
        }

        Object rawHostname = e.getRawData().get("hostname");
        if (rawHostname instanceof String raw) {
            String hostname = EntityNormalizer.hostname(raw);
            if (hostname != null) {
                p.put("hostname",    hostname);
                p.put("processName", e.getProcessName());
                p.put("tenantId", tl);

                neo4jClient.query("""
                        MATCH (f:FileHash {tenantId: $tenantId, hash: $hash})
                        MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})
                        MERGE (f)-[r:EXECUTED_ON]->(h)
                        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                      r.processName = $processName,
                                      r.firstEventId = $eventId, r.lastEventId = $eventId
                        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                      r.lastEventId = $eventId
                        """).bindAll(p).run();

                if (procName != null) {
                    Map<String, Object> ph = new HashMap<>();
                    ph.put("name",     procName);
                    ph.put("hostname", hostname);
                    ph.put("now",      now.toString());
                    ph.put("eventId",  eventId);
                    ph.put("tenantId", tl);
                    neo4jClient.query("""
                            MATCH (proc:Process: {tenantId: $tenantId, name: $name})
                            MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})
                            MERGE (proc)-[r:EXECUTED_ON]->(h)
                            """ + REL_UPSERT).bindAll(ph).run();
                }
            }
        }
    }

    private void saveNetwork(NetworkEvent e, LocalDateTime now, String eventId, String tl) {
        String srcIp = EntityNormalizer.ip(e.getSrcIp());
        String dstIp = EntityNormalizer.ip(e.getDstIp());
        if (srcIp == null || dstIp == null) return;

        Map<String, Object> p = new HashMap<>();
        p.put("srcAddress", srcIp);
        p.put("dstAddress", dstIp);
        p.put("dstPort",    e.getDstPort());
        p.put("now",        now.toString());
        p.put("eventId",    eventId);
        p.put("tenantId", tl);

        neo4jClient.query("""
                MERGE (src:IP {tenantId: $tenantId,address: $srcAddress})
                MERGE (dst:IP {tenantId: $tenantId, address: $dstAddress})
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
            dp.put("tenantId", tl);

            neo4jClient.query("""
                    MATCH (ip:IP {tenantId: $tenantId, address: $address})
                    MERGE (d:Domain {tenantId: $tenantId, name: $domain})
                    MERGE (ip)-[r:RESOLVES_TO]->(d)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(dp).run();
        }
    }

    private void saveAlert(AlertEvent e, LocalDateTime now, String eventId, String tl) {
        String targetUser     = EntityNormalizer.username(e.getTargetUser());
        String targetIp       = EntityNormalizer.ip(e.getTargetIp());
        String targetHost     = EntityNormalizer.hostname(e.getTargetHost());
        String targetDomain   = EntityNormalizer.domain(e.getTargetDomain());
        String targetFileHash = EntityNormalizer.hash(e.getTargetFileHash());

        if (targetUser != null && targetIp != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("username",  targetUser);
            p.put("address",   targetIp);
            p.put("alertName", e.getAlertName());
            p.put("severity",  e.getSeverity());
            p.put("now",       now.toString());
            p.put("eventId",   eventId);
            p.put("tenantId", tl);

            neo4jClient.query("""
                    MERGE (u:User {tenantId: $tenantId, username: $username})
                    MERGE (ip:IP {tenantId: $tenantId, address: $address})
                    MERGE (u)-[r:ALERTED_FROM]->(ip)
                    ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                  r.alertName = $alertName, r.severity = $severity,
                                  r.firstEventId = $eventId, r.lastEventId = $eventId
                    ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                  r.lastEventId = $eventId
                    """).bindAll(p).run();
        }

        if (targetFileHash != null && targetIp != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("hash",    targetFileHash);
            p.put("address", targetIp);
            p.put("now",     now.toString());
            p.put("eventId", eventId);
            p.put("tenantId", tl);

            neo4jClient.query("""
                    MERGE (f:FileHash {tenantId: $tenantId, hash: $hash})
                    MERGE (ip:IP {tenantId: $tenantId, address: $address})
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
            p.put("tenantId", tl);

            neo4jClient.query("MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})").bindAll(p).run();

            if (targetIp != null) {
                p.put("address", targetIp);
                
                neo4jClient.query("""
                        MATCH (h:Host {tenantId: $tenantId, hostname: $hostname})
                        MERGE (ip:IP {tenantId: $tenantId, address: $address})
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
            p.put("tenantId", tl);

            neo4jClient.query("MERGE (d:Domain {tenantId: $tenantId, name: $domain})").bindAll(p).run();

            if (targetIp != null) {
                p.put("address", targetIp);
                neo4jClient.query("""
                        MATCH (d:Domain {tenantId: $tenantId, name: $domain})
                        MERGE (ip:IP {tenantId: $tenantId, address: $address})
                        MERGE (ip)-[r:RESOLVES_TO]->(d)
                        ON CREATE SET r.firstSeen = $now, r.lastSeen = $now, r.count = 1,
                                      r.firstEventId = $eventId, r.lastEventId = $eventId
                        ON MATCH SET  r.lastSeen = $now, r.count = r.count + 1,
                                      r.lastEventId = $eventId
                        """).bindAll(p).run();
            }
        }

        // ── URL ──
        String targetUrl = EntityNormalizer.url(e.getTargetUrl());
        if (targetUrl != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("url",     targetUrl);
            p.put("now",     now.toString());
            p.put("eventId", eventId);
            p.put("tenantId", tl);
            neo4jClient.query("MERGE (:Url {tenantId: $tenantId, url: $url})").bindAll(p).run();

            if (targetIp != null) {
                p.put("address", targetIp);
                neo4jClient.query("""
                        MATCH (ip:IP {tenantId: $tenantId, address: $address})
                        MERGE (u:Url {tenantId: $tenantId, url: $url})
                        MERGE (ip)-[r:ACCESSED]->(u)
                        """ + REL_UPSERT).bindAll(p).run();
            }
        }

        // ── Process ──
        String targetProcess = EntityNormalizer.processName(e.getTargetProcess());
        if (targetProcess != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("name",    targetProcess);
            p.put("now",     now.toString());
            p.put("eventId", eventId);
            p.put("tenantId", tl);
            neo4jClient.query("MERGE (:Process {tenantId: $tenantId, name: $name})").bindAll(p).run();

            if (targetHost != null) {
                p.put("hostname", targetHost);
                neo4jClient.query("""
                        MATCH (proc:Process {tenantId: $tenantId, name: $name})
                        MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})
                        MERGE (proc)-[r:EXECUTED_ON]->(h)
                        """+ REL_UPSERT).bindAll(p).run();
            }
        }

        // ── CloudResource ──
        String targetCloudResourceId = e.getTargetCloudResourceId();
        if (targetCloudResourceId != null && !targetCloudResourceId.isBlank()) {
            targetCloudResourceId = targetCloudResourceId.trim();
            Map<String, Object> p = new HashMap<>();
            p.put("resourceId", targetCloudResourceId);
            p.put("now",        now.toString());
            p.put("eventId",    eventId);
            p.put("tenantId", tl);
            neo4jClient.query("MERGE (:CloudResource {tenantId: $tenantId, resourceId: $resourceId})").bindAll(p).run();

            if (targetUser != null) {
                p.put("username", targetUser);
                neo4jClient.query("""
                        MATCH (u:User {tenantId: $tenantId, username: $username})
                        MERGE (cr:CloudResource {tenantId: $tenantId, resourceId: $resourceId})
                        MERGE (u)-[r:ACCESSED]->(cr)
                        """ + REL_UPSERT).bindAll(p).run();
            }
        }

        // ── Email ──
        String targetEmail = EntityNormalizer.email(e.getTargetEmail());
        if (targetEmail != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("address", targetEmail);
            p.put("now",     now.toString());
            p.put("eventId", eventId);
            p.put("tenantId", tl);
            neo4jClient.query("MERGE (:Email {tenantId: $tenantId, address: $address})").bindAll(p).run();

            if (targetUser != null) {
                p.put("username", targetUser);
                neo4jClient.query("""
                        MATCH (u:User {tenantId: $tenantId, username: $username})
                        MERGE (em:Email {tenantId: $tenantId, address: $address})
                        MERGE (u)-[r:HAS_EMAIL]->(em)
                        """ + REL_UPSERT).bindAll(p).run();
            }
        }

        // ── CVE ──
        String targetCve = EntityNormalizer.cveId(e.getTargetCve());
        if (targetCve != null) {
            Map<String, Object> p = new HashMap<>();
            p.put("cveId",   targetCve);
            p.put("now",     now.toString());
            p.put("eventId", eventId);
            p.put("tenantId", tl);
            neo4jClient.query("MERGE (:Cve {tenantId: $tenantId, cveId: $cveId})").bindAll(p).run();

            if (targetHost != null) {
                p.put("hostname", targetHost);
                neo4jClient.query("""
                        MATCH (cve:Cve {tenantId: $tenantId, cveId: $cveId})
                        MERGE (h:Host {tenantId: $tenantId, hostname: $hostname})
                        MERGE (cve)-[r:AFFECTS]->(h)
                        """+ REL_UPSERT).bindAll(p).run();
            }
        }
    }
}
