package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.GraphDedupLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.GraphDedupLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphDeduplicationService {

    private final Neo4jClient neo4jClient;
    private final GraphDedupLogRepository dedupLogRepository;
    private final DedupSignal dedupSignal;

    @Scheduled(fixedDelayString = "${soc.dedup.interval-ms:120000}")
    public void runDeduplication() {
        if (!dedupSignal.getAndReset()) {
//            log.info("No new entity -> terminate cron deup job");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<GraphDedupLog> logs = new ArrayList<>();
        logs.addAll(deduplicateUsers(now));
        logs.addAll(deduplicateHosts(now));

        if (!logs.isEmpty()) {
            dedupLogRepository.saveAll(logs);
            log.info("[Dedup] Created {} SAME_AS links", logs.size());
        }
    }

    private List<GraphDedupLog> deduplicateUsers(LocalDateTime now) {
        String nowStr = now.toString();
        // Confidence = base(0.50) + sharedHost(+0.25) + sharedIp(+0.15)
        // Both OPTIONAL MATCH run in the same query plan — no extra round trip
        Collection<Map<String, Object>> created = neo4jClient.query("""
                MATCH (u2:User)
                WHERE u2.username CONTAINS '@'
                WITH u2, split(u2.username, '@')[0] AS prefix
                MATCH (u1:User {username: prefix})
                WHERE u1 <> u2
                  AND u1.tenantId = u2.tenantId
                  AND NOT (u1)-[:SAME_AS]-(u2)
                OPTIONAL MATCH (u1)-[:LOGGED_IN_TO]->(sh:Host)<-[:LOGGED_IN_TO]-(u2)
                WITH u1, u2, count(DISTINCT sh) AS sharedHosts
                OPTIONAL MATCH (u1)-[:ALERTED_FROM]->(si:IP)<-[:ALERTED_FROM]-(u2)
                WITH u1, u2, sharedHosts, count(DISTINCT si) AS sharedIps
                WITH u1, u2,
                     0.50
                     + CASE WHEN sharedHosts > 0 THEN 0.25 ELSE 0.0 END
                     + CASE WHEN sharedIps   > 0 THEN 0.15 ELSE 0.0 END AS conf
                MERGE (u1)-[r:SAME_AS]->(u2)
                ON CREATE SET r.confidence = conf,
                              r.reason     = 'email_prefix',
                              r.detectedAt = $now,
                              r.tenantId   = u2.tenantId
                RETURN u1.username AS fromVal, u2.username AS toVal, conf AS confidence
                """)
                .bind(nowStr).to("now")
                .fetch().all();

        List<GraphDedupLog> logs = new ArrayList<>();
        created.forEach(row -> {
            double conf = row.get("confidence") instanceof Number n ? n.doubleValue() : 0.50;
            logs.add(GraphDedupLog.builder()
                    .id(UUID.randomUUID().toString())
                    .fromNode("User:" + row.get("fromVal"))
                    .toNode("User:" + row.get("toVal"))
                    .rule("email_prefix")
                    .confidence(conf)
                    .detectedAt(now)
                    .build());
        });
        return logs;
    }

    private List<GraphDedupLog> deduplicateHosts(LocalDateTime now) {
        String nowStr = now.toString();
        // Confidence = base(0.45) + sharedIp(+0.40)
        // An IP that authenticated to both h1 and h2 is very strong evidence (same machine)
        Collection<Map<String, Object>> created = neo4jClient.query("""
                MATCH (h2:Host)
                WHERE h2.hostname CONTAINS '.'
                WITH h2, split(h2.hostname, '.')[0] AS shortname
                MATCH (h1:Host {hostname: shortname})
                WHERE h1 <> h2
                  AND h1.tenantId = h2.tenantId
                  AND NOT (h1)-[:SAME_AS]-(h2)
                OPTIONAL MATCH (ip:IP)-[:AUTHENTICATED_TO]->(h1)
                WITH h1, h2, collect(DISTINCT ip) AS ips1
                OPTIONAL MATCH (ip2:IP)-[:AUTHENTICATED_TO]->(h2)
                  WHERE ip2 IN ips1
                WITH h1, h2, count(ip2) AS sharedIpCount
                WITH h1, h2,
                     0.45
                     + CASE WHEN sharedIpCount > 0 THEN 0.40 ELSE 0.0 END AS conf
                MERGE (h1)-[r:SAME_AS]->(h2)
                ON CREATE SET r.confidence = conf,
                              r.reason     = 'fqdn_shortname',
                              r.detectedAt = $now,
                              r.tenantId   = h2.tenantId
                RETURN h1.hostname AS fromVal, h2.hostname AS toVal, conf AS confidence
                """)
                .bind(nowStr).to("now")
                .fetch().all();

        List<GraphDedupLog> logs = new ArrayList<>();
        created.forEach(row -> {
            double conf = row.get("confidence") instanceof Number n ? n.doubleValue() : 0.45;
            logs.add(GraphDedupLog.builder()
                    .id(UUID.randomUUID().toString())
                    .fromNode("Host:" + row.get("fromVal"))
                    .toNode("Host:" + row.get("toVal"))
                    .rule("fqdn_shortname")
                    .confidence(conf)
                    .detectedAt(now)
                    .build());
        });
        return logs;
    }
}
