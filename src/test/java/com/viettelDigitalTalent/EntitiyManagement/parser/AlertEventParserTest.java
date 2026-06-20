package com.viettelDigitalTalent.EntitiyManagement.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.alert.AlertEventParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertEventParserTest {

    private AlertEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new AlertEventParser();
        ReflectionTestUtils.setField(parser, "objectMapper", new ObjectMapper());
    }

    @Test
    void parsesFullAlertEvent() {
        String raw = "{\"alertName\":\"Brute Force Login\",\"severity\":\"HIGH\",\"targetUser\":\"admin\",\"targetIp\":\"185.220.101.42\"}";
        AlertEvent event = parser.parse(raw);
        assertThat(event.getAlertName()).isEqualTo("Brute Force Login");
        assertThat(event.getSeverity()).isEqualTo("HIGH");
        assertThat(event.getTargetUser()).isEqualTo("admin");
        assertThat(event.getTargetIp()).isEqualTo("185.220.101.42");
    }

    @Test
    void parsesFileHashAlert() {
        String raw = "{\"alertName\":\"Malware Detected\",\"severity\":\"CRITICAL\",\"targetFileHash\":\"d41d8cd98f00b204e9800998ecf8427e\"}";
        AlertEvent event = parser.parse(raw);
        assertThat(event.getTargetFileHash()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    void setsThreatCategory() {
        String raw = "{\"alertName\":\"Test Alert\",\"severity\":\"LOW\"}";
        AlertEvent event = parser.parse(raw);
        assertThat(event.getCategory()).isEqualTo("THREAT");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void storesFieldsInRawData() {
        String raw = "{\"alertName\":\"C2 Beacon\",\"severity\":\"HIGH\",\"targetIp\":\"1.2.3.4\"}";
        AlertEvent event = parser.parse(raw);
        assertThat(event.getRawData()).containsEntry("alertName", "C2 Beacon");
        assertThat(event.getRawData()).containsEntry("severity", "HIGH");
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not-json-at-all"))
                .isInstanceOf(RuntimeException.class);
    }
}
