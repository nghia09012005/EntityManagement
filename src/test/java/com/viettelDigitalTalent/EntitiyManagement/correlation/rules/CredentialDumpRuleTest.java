package com.viettelDigitalTalent.EntitiyManagement.correlation.rules;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CredentialDumpRuleTest {

    private final CredentialDumpRule rule = new CredentialDumpRule();
    private final LocalDateTime NOW = LocalDateTime.now();

    private AuditLog log(String eventId, Map<String, Object> rawData) {
        AuditLog l = new AuditLog();
        l.setEventId(eventId);
        l.setRawData(rawData);
        return l;
    }

    private AuditLog processEvent(String processName, String commandLine) {
        return log("e-" + System.nanoTime(),
                Map.of("processName", processName, "commandLine", commandLine));
    }

    @Test
    void returnsEmptyWhenNoEvents() {
        Optional<Incident> result = rule.evaluate(List.of(), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoCommandLine() {
        AuditLog event = log("e1", Map.of("processName", "cmd.exe"));
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoKeyword() {
        AuditLog event = processEvent("calc.exe", "calc.exe /launch");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsIncidentForMimikatz() {
        AuditLog event = processEvent("cmd.exe", "mimikatz.exe privilege::debug sekurlsa::logonpasswords");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("CredentialDump");
        assertThat(result.get().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void returnsIncidentForLsadump() {
        AuditLog event = processEvent("cmd.exe", "Invoke-Mimikatz -Command lsadump::sam");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("CredentialDump");
    }

    @Test
    void returnsIncidentForSekurlsa() {
        AuditLog event = processEvent("powershell.exe", "sekurlsa::logonpasswords");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
    }

    @Test
    void returnsIncidentForLsass() {
        AuditLog event = processEvent("procdump.exe", "procdump lsass -ma lsass.dmp");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("CredentialDump");
    }
}