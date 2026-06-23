package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphEntityService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphEntityServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    private GraphEntityService service;

    @BeforeEach
    void setUp() {
        service = new GraphEntityService(neo4jClient, new SimpleMeterRegistry(), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    // ── AuthenticationEvent ──────────────────────────────────────────────────

    @Test
    void saveAuth_callsNeo4jForUserAndHost() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setUsername("admin");
        event.setWorkstation("WIN-PC01");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveAuth_skipsWhenUsernameNull() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setWorkstation("WIN-PC01");

        service.save(event);

        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveAuth_skipsWhenWorkstationNull() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setUsername("admin");

        service.save(event);

        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveAuth_withIpCreatesAdditionalQuery() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setUsername("admin");
        event.setWorkstation("WIN-PC01");
        event.setIpAddress("192.168.1.1");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        // 1 query for User-Host, 1 query for IP-Host
        verify(neo4jClient, times(2)).query(anyString());
    }

    // ── ProcessEvent ─────────────────────────────────────────────────────────

    @Test
    void saveProcess_createsFileHashNode() {
        ProcessEvent event = new ProcessEvent();
        event.setFileHash("d41d8cd98f00b204e9800998ecf8427e");
        event.setProcessName("mimikatz.exe");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveProcess_skipsWhenFileHashNull() {
        ProcessEvent event = new ProcessEvent();
        event.setProcessName("cmd.exe");

        service.save(event);

        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveProcess_withHostnameCreatesExecutedOnRelation() {
        ProcessEvent event = new ProcessEvent();
        event.setFileHash("abc123");
        event.setProcessName("powershell.exe");
        event.getRawData().put("hostname", "WIN-PC01");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        // 1 for FileHash MERGE, 1 for EXECUTED_ON relationship
        verify(neo4jClient, times(2)).query(anyString());
    }

    // ── NetworkEvent ─────────────────────────────────────────────────────────

    @Test
    void saveNetwork_createsTwoIpNodes() {
        NetworkEvent event = new NetworkEvent();
        event.setSrcIp("10.0.0.1");
        event.setDstIp("185.220.101.42");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveNetwork_skipsWhenSrcIpNull() {
        NetworkEvent event = new NetworkEvent();
        event.setDstIp("8.8.8.8");

        service.save(event);

        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveNetwork_skipsWhenDstIpNull() {
        NetworkEvent event = new NetworkEvent();
        event.setSrcIp("10.0.0.1");
        // no dstIp
        service.save(event);
        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveNetwork_withDomainCreatesResolvesToRelation() {
        NetworkEvent event = new NetworkEvent();
        event.setSrcIp("10.0.0.1");
        event.setDstIp("185.220.101.42");
        event.setDstDomain("malware-c2.example.com");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        // 1 for CONNECTED_TO, 1 for RESOLVES_TO
        verify(neo4jClient, times(2)).query(anyString());
    }

    // ── AlertEvent ───────────────────────────────────────────────────────────

    @Test
    void saveAlert_withUserAndIpCreatesAlertedFromRelation() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Brute Force");
        event.setSeverity("HIGH");
        event.setTargetUser("admin");
        event.setTargetIp("185.220.101.42");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveAlert_withFileHashAndIpCreatesDetectedOnRelation() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Malware Detected");
        event.setSeverity("CRITICAL");
        event.setTargetFileHash("d41d8cd98f00b204e9800998ecf8427e");
        event.setTargetIp("192.168.1.101");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveAlert_withHostCreatesHostNode() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Lateral Movement");
        event.setSeverity("HIGH");
        event.setTargetHost("WIN-DC01");
        event.setTargetIp("192.168.1.100");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        // 1 for Host MERGE, 1 for TARGETED_AT
        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveAlert_withDomainCreatesDomainNode() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("C2 Beacon");
        event.setSeverity("HIGH");
        event.setTargetDomain("malware-c2.example.com");
        event.setTargetIp("185.220.101.42");
        event.setTimestamp(LocalDateTime.now());

        service.save(event);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void save_doesNotThrowOnException() {
        when(neo4jClient.query(anyString())).thenThrow(new RuntimeException("Neo4j down"));

        AuthenticationEvent event = new AuthenticationEvent();
        event.setUsername("admin");
        event.setWorkstation("WIN-PC01");

        // should not propagate exception
        service.save(event);
    }

    @Test
    void saveAuth_withEventIdPropagated() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setEventId("uuid-test-001");
        event.setUsername("bob");
        event.setWorkstation("LAP-01");
        event.setTimestamp(LocalDateTime.now());
        service.save(event);
        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void saveAlert_skipsAlertedFromWhenMissingIp() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Test Alert");
        event.setSeverity("LOW");
        event.setTargetUser("admin");
        // no targetIp → ALERTED_FROM should NOT be created, no other relations either
        event.setTimestamp(LocalDateTime.now());
        service.save(event);
        verify(neo4jClient, never()).query(anyString());
    }

    @Test
    void saveAlert_withHostAndNoIpCreatesOnlyHostMerge() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Test");
        event.setSeverity("MEDIUM");
        event.setTargetHost("WIN-DC01");
        // no targetIp → only Host MERGE, no TARGETED_AT
        event.setTimestamp(LocalDateTime.now());
        service.save(event);
        verify(neo4jClient, times(1)).query(anyString());
    }

    @Test
    void saveAlert_withDomainAndIpCreatesTwoQueries() {
        AlertEvent event = new AlertEvent();
        event.setAlertName("Domain Beacon");
        event.setSeverity("HIGH");
        event.setTargetDomain("evil.com");
        event.setTargetIp("1.2.3.4");
        event.setTimestamp(LocalDateTime.now());
        service.save(event);
        // Domain MERGE + RESOLVES_TO = 2 queries
        verify(neo4jClient, times(2)).query(anyString());
    }

    @Test
    void saveProcess_withNullTimestampUsesNow() {
        ProcessEvent event = new ProcessEvent();
        event.setFileHash("abc123");
        event.setTimestamp(null); // should default to now
        service.save(event);
        verify(neo4jClient, atLeastOnce()).query(anyString());
    }
}
