package com.viettelDigitalTalent.EntitiyManagement.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jSchemaInitializer {

    private final Neo4jClient neo4jClient;

    @PostConstruct
    public void initSchema() {
        // Drop global uniqueness constraints — không dùng được với multi-tenant
        // (hai tenant khác nhau có thể có cùng username/address/...)
        dropGlobalUniqueConstraints();

        // Tạo index thường để tăng tốc MATCH/MERGE trong mỗi tenant
        List<String> indexes = List.of(
        "CREATE INDEX idx_user_tenant_username IF NOT EXISTS FOR (n:User) ON (n.tenantId, n.username)",
        "CREATE INDEX idx_host_tenant_hostname IF NOT EXISTS FOR (n:Host) ON (n.tenantId, n.hostname)",
        "CREATE INDEX idx_ip_tenant_address    IF NOT EXISTS FOR (n:IP)   ON (n.tenantId, n.address)",
        "CREATE INDEX idx_domain_tenant_name   IF NOT EXISTS FOR (n:Domain) ON (n.tenantId, n.name)",
        "CREATE INDEX idx_file_tenant_hash     IF NOT EXISTS FOR (n:FileHash) ON (n.tenantId, n.hash)",
        "CREATE INDEX idx_url_tenant_url       IF NOT EXISTS FOR (n:Url)      ON (n.tenantId, n.url)",
        "CREATE INDEX idx_proc_tenant_name     IF NOT EXISTS FOR (n:Process)  ON (n.tenantId, n.name)",
        "CREATE INDEX idx_cloud_tenant_id      IF NOT EXISTS FOR (n:CloudResource) ON (n.tenantId, n.resourceId)",
        "CREATE INDEX idx_email_tenant_addr    IF NOT EXISTS FOR (n:Email)    ON (n.tenantId, n.address)",
        "CREATE INDEX idx_cve_tenant_id        IF NOT EXISTS FOR (n:Cve)      ON (n.tenantId, n.cveId)"
    );

        int done = 0;
        for (String stmt : indexes) {
            try { neo4jClient.query(stmt).run(); done++; }
            catch (Exception e) { log.warn("[Schema] Index skip: {}", e.getMessage()); }
        }
        log.info("[Schema] Neo4j indexes: {}/{} created", done, indexes.size());
    }

    private void dropGlobalUniqueConstraints() {
        try {
            Collection<Map<String, Object>> constraints = neo4jClient
                    .query("SHOW CONSTRAINTS YIELD name, type WHERE type = 'UNIQUENESS'")
                    .fetch().all();

            for (Map<String, Object> row : constraints) {
                String name = (String) row.get("name");
                try {
                    neo4jClient.query("DROP CONSTRAINT " + name + " IF EXISTS").run();
                    log.info("[Schema] Dropped global constraint: {}", name);
                } catch (Exception e) {
                    log.warn("[Schema] Cannot drop constraint {}: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Schema] SHOW CONSTRAINTS failed (Neo4j version?): {}", e.getMessage());
        }
    }
}
