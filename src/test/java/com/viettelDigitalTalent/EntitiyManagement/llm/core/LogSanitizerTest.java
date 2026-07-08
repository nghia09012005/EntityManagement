package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    private final LogSanitizer sanitizer = new LogSanitizer();

    @Test
    void shouldRestoreMaskedValuesBackToOriginalValues() {
        String originalLog = "Failed login for admin@example.com from 192.168.1.10 to https://example.com";

        LogSanitizer.SanitizedLog sanitized = sanitizer.sanitizeWithMapping(originalLog);

        assertThat(sanitized.getSanitizedLog()).contains("<EMAIL>").contains("<IP>").contains("<URL>");

        AlertEvent event = new AlertEvent();
        event.setAlertName("Suspicious login from <IP>");
        event.setDescription("User admin@example.com from <IP> visited <URL>");
        event.setTargetIp("<IP>");
        event.setTargetUser("<EMAIL>");
        event.setTargetUrl("<URL>");

        AlertEvent restored = sanitizer.restoreMaskedValues(event, sanitized.getReplacements());

        assertThat(restored.getAlertName()).isEqualTo("Suspicious login from 192.168.1.10");
        assertThat(restored.getDescription()).contains("admin@example.com").contains("192.168.1.10").contains("https://example.com");
        assertThat(restored.getTargetIp()).isEqualTo("192.168.1.10");
        assertThat(restored.getTargetUser()).isEqualTo("admin@example.com");
        assertThat(restored.getTargetUrl()).isEqualTo("https://example.com");
    }

    @Test
    void shouldRestoreMultipleMaskedValuesInTheSameText() {
        String originalLog = "from 192.168.1.10 to 10.0.0.5 and admin@example.com";

        LogSanitizer.SanitizedLog sanitized = sanitizer.sanitizeWithMapping(originalLog);
        AlertEvent event = new AlertEvent();
        event.setDescription("Observed traffic from <IP> to <IP> by <EMAIL>");
        event.setTargetIp("<IP>");
        event.setTargetUser("<EMAIL>");

        AlertEvent restored = sanitizer.restoreMaskedValues(event, sanitized.getReplacements());

        assertThat(restored.getDescription()).contains("192.168.1.10").contains("10.0.0.5").contains("admin@example.com");
        assertThat(restored.getTargetIp()).isEqualTo("192.168.1.10");
        assertThat(restored.getTargetUser()).isEqualTo("admin@example.com");
    }
}
