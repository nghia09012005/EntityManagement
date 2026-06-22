package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmOutputValidator;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LlmOutputValidatorTest {

    private LlmOutputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LlmOutputValidator();
        ReflectionTestUtils.setField(validator, "objectMapper", new ObjectMapper());
    }

    @Test
    void validJsonProducesAlertEvent() {
        String json = "{\"alertName\":\"Brute Force\",\"severity\":\"HIGH\",\"description\":\"Multiple failed logins\"," +
                "\"targetIp\":\"192.168.1.1\",\"targetUser\":\"admin\",\"targetHost\":\"DC01\"," +
                "\"targetDomain\":null,\"targetFileHash\":null,\"source\":\"syslog\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event).isNotNull();
        assertThat(event.getAlertName()).isEqualTo("Brute Force");
        assertThat(event.getSeverity()).isEqualTo("HIGH");
        assertThat(event.getTargetIp()).isEqualTo("192.168.1.1");
        assertThat(event.getTargetUser()).isEqualTo("admin");
    }

    @Test
    void stripsMarkdownCodeBlock() {
        String md = "```json\n{\"alertName\":\"Test\",\"severity\":\"LOW\"}\n```";
        AlertEvent event = validator.validate(md);
        assertThat(event).isNotNull();
        assertThat(event.getAlertName()).isEqualTo("Test");
    }

    @Test
    void stripsMarkdownBlockWithoutLanguage() {
        String md = "```\n{\"alertName\":\"X\",\"severity\":\"HIGH\"}\n```";
        AlertEvent event = validator.validate(md);
        assertThat(event).isNotNull();
        assertThat(event.getAlertName()).isEqualTo("X");
    }

    @Test
    void lowercaseSeverityIsNormalizedToUppercase() {
        String json = "{\"alertName\":\"X\",\"severity\":\"medium\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void invalidSeverityDefaultsToMedium() {
        String json = "{\"alertName\":\"X\",\"severity\":\"EXTREME\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void nullSeverityDefaultsToMedium() {
        String json = "{\"alertName\":\"X\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void missingAlertNameReturnsNull() {
        String json = "{\"severity\":\"HIGH\",\"description\":\"no name\"}";
        assertThat(validator.validate(json)).isNull();
    }

    @Test
    void emptyAlertNameReturnsNull() {
        String json = "{\"alertName\":\"\",\"severity\":\"HIGH\"}";
        assertThat(validator.validate(json)).isNull();
    }

    @Test
    void nullInputReturnsNull() {
        assertThat(validator.validate(null)).isNull();
    }

    @Test
    void blankInputReturnsNull() {
        assertThat(validator.validate("   ")).isNull();
    }

    @Test
    void malformedJsonReturnsNull() {
        assertThat(validator.validate("not-json-at-all")).isNull();
    }

    @Test
    void nullJsonFieldsMapToNull() {
        String json = "{\"alertName\":\"Y\",\"severity\":\"LOW\",\"targetIp\":null,\"targetUser\":null}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getTargetIp()).isNull();
        assertThat(event.getTargetUser()).isNull();
    }

    @Test
    void categoryIsSetToThreat() {
        String json = "{\"alertName\":\"Z\",\"severity\":\"LOW\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getCategory()).isEqualTo("THREAT");
    }

    @Test
    void allTargetFieldsMapped() {
        String json = "{\"alertName\":\"Full\",\"severity\":\"CRITICAL\"," +
                "\"targetIp\":\"1.2.3.4\",\"targetUser\":\"admin\",\"targetHost\":\"DC01\"," +
                "\"targetDomain\":\"evil.com\",\"targetFileHash\":\"abc123\",\"source\":\"test\"}";
        AlertEvent event = validator.validate(json);
        assertThat(event.getTargetIp()).isEqualTo("1.2.3.4");
        assertThat(event.getTargetUser()).isEqualTo("admin");
        assertThat(event.getTargetHost()).isEqualTo("DC01");
        assertThat(event.getTargetDomain()).isEqualTo("evil.com");
        assertThat(event.getTargetFileHash()).isEqualTo("abc123");
        assertThat(event.getSource()).isEqualTo("test");
    }

    @Test
    void criticalSeverityPreserved() {
        String json = "{\"alertName\":\"A\",\"severity\":\"CRITICAL\"}";
        assertThat(validator.validate(json).getSeverity()).isEqualTo("CRITICAL");
    }
}
