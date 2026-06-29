package com.viettelDigitalTalent.EntitiyManagement.correlation.rules;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class C2BeaconRuleTest {

    private final C2BeaconRule rule = new C2BeaconRule();
    private final LocalDateTime NOW = LocalDateTime.now();

    private AuditLog log(String eventId, Map<String, Object> rawData) {
        AuditLog l = new AuditLog();
        l.setEventId(eventId);
        l.setRawData(rawData);
        return l;
    }

    private AuditLog networkEvent(String dstDomain) {
        return log("e-" + System.nanoTime(),
                Map.of("dstDomain", dstDomain, "srcIp", "10.0.0.1", "dstPort", "443"));
    }

    @Test
    void returnsEmptyWhenNoEvents() {
        Optional<Incident> result = rule.evaluate(List.of(), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoDstDomain() {
        AuditLog event = log("e1", Map.of("srcIp", "10.0.0.1", "dstPort", "443"));
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenLessThanThreeConnections() {
        List<AuditLog> events = List.of(
                networkEvent("evil.cc"),
                networkEvent("evil.cc")
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsIncidentWhenThreeOrMoreConnections() {
        List<AuditLog> events = List.of(
                networkEvent("evil.cc"),
                networkEvent("evil.cc"),
                networkEvent("evil.cc")
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isPresent();
        Incident incident = result.get();
        assertThat(incident.getPatternName()).isEqualTo("C2Beacon");
        assertThat(incident.getMitreId()).isEqualTo("T1071");
    }

    @Test
    void returnsFirstDomainThatHitsThreshold() {
        List<AuditLog> events = new ArrayList<>();
        // domain-a has 2 events (below threshold)
        events.add(networkEvent("domain-a.com"));
        events.add(networkEvent("domain-a.com"));
        // domain-b has 4 events (above threshold)
        events.add(networkEvent("domain-b.com"));
        events.add(networkEvent("domain-b.com"));
        events.add(networkEvent("domain-b.com"));
        events.add(networkEvent("domain-b.com"));

        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isPresent();
        // the incident should be about the domain that hit threshold (domain-b.com)
        assertThat(result.get().getTitle()).contains("domain-b.com");
    }
}