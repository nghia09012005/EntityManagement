package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmPromptEngine;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.MockLlmClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptEngineTest {

    private final LlmPromptEngine engine = new LlmPromptEngine();

    @Test
    void buildIncludesInputLogLine() {
        String result = engine.build("Failed password for admin from 1.2.3.4");
        assertThat(result).contains("Failed password for admin from 1.2.3.4");
    }

    @Test
    void buildIncludesAlertNameField() {
        String result = engine.build("test input");
        assertThat(result).contains("alertName");
    }

    @Test
    void buildIncludesSeverityField() {
        String result = engine.build("test");
        assertThat(result).contains("severity");
    }

    @Test
    void buildIncludesTargetIpField() {
        String result = engine.build("test");
        assertThat(result).contains("targetIp");
    }

    @Test
    void buildIsNotEmpty() {
        assertThat(engine.build("anything")).isNotBlank();
    }

    @Test
    void mockClientReturnsValidJsonWithAlertName() {
        MockLlmClient mock = new MockLlmClient();
        String output = mock.call("any prompt");
        assertThat(output).contains("alertName");
        assertThat(output).contains("severity");
        assertThat(mock.modelName()).isEqualTo("mock-llm");
    }

    @Test
    void mockClientOutputContainsMediumSeverity() {
        MockLlmClient mock = new MockLlmClient();
        assertThat(mock.call("prompt")).contains("MEDIUM");
    }
}
