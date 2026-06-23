package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.GraphDedupLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.GraphDedupLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphDeduplicationService {

    private final Neo4jClient neo4jClient;
    private final GraphDedupLogRepository dedupLogRepository;

    @Scheduled(fixedDelayString = "${soc.dedup.interval-ms:120000}")
    public void runDeduplication() {
        LocalDateTime now = LocalDateTime.now();
        int users = deduplicateUsers(now);
        int hosts = deduplicateHosts(now);
        if (users + hosts > 0) {
            log.info("[Dedup] Created {} SAME_AS links: {} users, {} hosts", users + hosts, users, hosts);
        }
    }

    private int deduplicateUsers(LocalDateTime now) {
        String nowStr = now.toString();
        Collection<Map<String, Object>> created = neo4jClient.query("""
                MATCH (u2:User)
                WHERE u2.username CONTAINS '@'
                WITH u2, split(u2.username, '@')[0] AS prefix
                MATCH (u1:User {username: prefix})
                WHERE u1 <> u2
                  AND NOT (u1)-[:SAME_AS]-(u2)
                MERGE (u1)-[r:SAME_AS]->(u2)
                ON CREATE SET r.confidence = 0.85,
                              r.reason     = 'email_prefix',
                              r.detectedAt = $now
                RETURN u1.username AS fromVal, u2.username AS toVal
                """)
                .bind(nowStr).to("now")
                .fetch().all();

        created.forEach(row -> dedupLogRepository.save(GraphDedupLog.builder()
                .id(UUID.randomUUID().toString())
                .fromNode("User:" + row.get("fromVal"))
                .toNode("User:" + row.get("toVal"))
                .rule("email_prefix")
                .confidence(0.85)
                .detectedAt(now)
                .build()));

        return created.size();
    }

    private int deduplicateHosts(LocalDateTime now) {
        String nowStr = now.toString();
        Collection<Map<String, Object>> created = neo4jClient.query("""
                MATCH (h2:Host)
                WHERE h2.hostname CONTAINS '.'
                WITH h2, split(h2.hostname, '.')[0] AS shortname
                MATCH (h1:Host {hostname: shortname})
                WHERE h1 <> h2
                  AND NOT (h1)-[:SAME_AS]-(h2)
                MERGE (h1)-[r:SAME_AS]->(h2)
                ON CREATE SET r.confidence = 0.80,
                              r.reason     = 'fqdn_shortname',
                              r.detectedAt = $now
                RETURN h1.hostname AS fromVal, h2.hostname AS toVal
                """)
                .bind(nowStr).to("now")
                .fetch().all();

        created.forEach(row -> dedupLogRepository.save(GraphDedupLog.builder()
                .id(UUID.randomUUID().toString())
                .fromNode("Host:" + row.get("fromVal"))
                .toNode("Host:" + row.get("toVal"))
                .rule("fqdn_shortname")
                .confidence(0.80)
                .detectedAt(now)
                .build()));

        return created.size();
    }
}
