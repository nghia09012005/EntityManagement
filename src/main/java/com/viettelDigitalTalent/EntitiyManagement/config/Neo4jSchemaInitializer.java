package com.viettelDigitalTalent.EntitiyManagement.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jSchemaInitializer {

    private final Neo4jClient neo4jClient;

    @PostConstruct
    public void createConstraints() {
        List<String> statements = List.of(
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:User)          REQUIRE n.username   IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Host)          REQUIRE n.hostname   IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:IP)            REQUIRE n.address    IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Domain)        REQUIRE n.name       IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:FileHash)      REQUIRE n.hash       IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Url)           REQUIRE n.url        IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Process)       REQUIRE n.name       IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:CloudResource) REQUIRE n.resourceId IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Email)         REQUIRE n.address    IS UNIQUE",
            "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Cve)           REQUIRE n.cveId      IS UNIQUE"
        );

        int created = 0;
        for (String stmt : statements) {
            try {
                neo4jClient.query(stmt).run();
                created++;
            } catch (Exception e) {
                log.warn("[Schema] Constraint skip: {}", e.getMessage());
            }
        }
        log.info("[Schema] Neo4j: {}/{} constraints applied", created, statements.size());
    }
}
