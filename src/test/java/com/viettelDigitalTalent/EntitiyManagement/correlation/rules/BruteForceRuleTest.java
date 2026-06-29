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
class BruteForceRuleTest {

    private final BruteForceRule rule = new BruteForceRule();
    private final LocalDateTime NOW = LocalDateTime.now();

    private AuditLog log(String eventId, Map<String, Object> rawData) {
        AuditLog l = new AuditLog();
        l.setEventId(eventId);
        l.setRawData(rawData);
        return l;
    }

    private AuditLog authEvent(String ip, String username, Object success) {
        return log("e-" + System.nanoTime(), Map.of("ipAddress", ip, "username", username, "success", success));
    }

    @Test
    void returnsEmptyWhenNoEvents() {
        Optional<Incident> result = rule.evaluate(List.of(), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenOnlyFailures_NoSuccess() {
        List<AuditLog> events = List.of(
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", false)
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenLessThanThreeFailures() {
        List<AuditLog> events = List.of(
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", true)
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsIncidentWhenThreeFailuresPlusSuccess() {
        List<AuditLog> events = List.of(
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", false),
                authEvent("1.2.3.4", "jdoe", true)
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isPresent();
        Incident incident = result.get();
        assertThat(incident.getPatternName()).isEqualTo("BruteForce");
        assertThat(incident.getMitreId()).isEqualTo("T1110");
        assertThat(incident.getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void handlesStringSuccessValues() {
        List<AuditLog> events = List.of(
                authEvent("1.2.3.4", "jdoe", "false"),
                authEvent("1.2.3.4", "jdoe", "false"),
                authEvent("1.2.3.4", "jdoe", "false"),
                authEvent("1.2.3.4", "jdoe", "true")
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("BruteForce");
    }

    @Test
    void handlesBooleanSuccessValues() {
        List<AuditLog> events = List.of(
                authEvent("1.2.3.4", "jdoe", Boolean.FALSE),
                authEvent("1.2.3.4", "jdoe", Boolean.FALSE),
                authEvent("1.2.3.4", "jdoe", Boolean.FALSE),
                authEvent("1.2.3.4", "jdoe", Boolean.TRUE)
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isPresent();
    }

    @Test
    void returnsEmptyWhenNoIpAddress() {
        List<AuditLog> events = List.of(
                log("e1", Map.of("username", "jdoe", "success", false)),
                log("e2", Map.of("username", "jdoe", "success", false)),
                log("e3", Map.of("username", "jdoe", "success", false)),
                log("e4", Map.of("username", "jdoe", "success", true))
        );
        Optional<Incident> result = rule.evaluate(events, NOW);
        assertThat(result).isEmpty();
    }
}