package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GeminiLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GroqLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmOutputValidator;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmPromptEngine;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LogSanitizer;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.MockLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmProcessTest {

    private LlmProcess llmProcess;

    @Mock GeminiLlmClient geminiClient;
    @Mock GroqLlmClient   groqClient;
    @Mock MockLlmClient   mockClient;
    @Mock LlmPromptEngine promptEngine;

    private LlmOutputValidator realValidator;

    @BeforeEach
    void setUp() {
        realValidator = new LlmOutputValidator();
        ReflectionTestUtils.setField(realValidator, "objectMapper", new ObjectMapper());

        llmProcess = new LlmProcess();
        ReflectionTestUtils.setField(llmProcess, "geminiClient",    geminiClient);
        ReflectionTestUtils.setField(llmProcess, "groqClient",      groqClient);
        ReflectionTestUtils.setField(llmProcess, "mockClient",      mockClient);
        ReflectionTestUtils.setField(llmProcess, "promptEngine",    promptEngine);
        ReflectionTestUtils.setField(llmProcess, "outputValidator", realValidator);
        ReflectionTestUtils.setField(llmProcess, "logSanitizer",    new LogSanitizer());

        lenient().when(promptEngine.build(anyString())).thenReturn("prompt");
        lenient().when(geminiClient.modelName()).thenReturn("gemini");
        lenient().when(groqClient.modelName()).thenReturn("groq");
        lenient().when(mockClient.modelName()).thenReturn("mock");
    }

    @Test
    void usesGeminiWhenItSucceeds() {
        when(geminiClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Brute Force\",\"severity\":\"HIGH\"}");

        AlertEvent result = llmProcess.extractAlert("ssh failed password for admin");

        assertThat(result).isNotNull();
        assertThat(result.getAlertName()).isEqualTo("Brute Force");
        verify(groqClient, never()).call(anyString());
        verify(mockClient, never()).call(anyString());
    }

    @Test
    void fallsBackToGroqWhenGeminiReturnsNull() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Groq Alert\",\"severity\":\"MEDIUM\"}");

        AlertEvent result = llmProcess.extractAlert("some log");

        assertThat(result.getAlertName()).isEqualTo("Groq Alert");
        verify(mockClient, never()).call(anyString());
    }

    @Test
    void fallsBackToGroqWhenGeminiReturnsInvalidJson() {
        when(geminiClient.call(anyString())).thenReturn("not json");
        when(groqClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Groq Alert\",\"severity\":\"LOW\"}");

        AlertEvent result = llmProcess.extractAlert("text");

        assertThat(result.getAlertName()).isEqualTo("Groq Alert");
    }

    @Test
    void throwsWhenBothGeminiAndGroqFail() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenReturn(null);

        assertThatThrownBy(() -> llmProcess.extractAlert("text"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockClient, never()).call(anyString());
    }

    @Test
    void throwsWhenAllClientsFail() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenReturn(null);

        assertThatThrownBy(() -> llmProcess.extractAlert("text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesGeminiExceptionAndFallsBackToGroq() {
        when(geminiClient.call(anyString())).thenThrow(new RuntimeException("503 overload"));
        when(groqClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Groq OK\",\"severity\":\"HIGH\"}");

        AlertEvent result = llmProcess.extractAlert("log");

        assertThat(result.getAlertName()).isEqualTo("Groq OK");
    }

    @Test
    void handlesGroqExceptionAndThrowsWhenNoClientSucceeds() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenThrow(new RuntimeException("groq down"));

        assertThatThrownBy(() -> llmProcess.extractAlert("text"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockClient, never()).call(anyString());
    }

    @Test
    void throwsWhenOptionalClientsAreMissing() {
        // Simulate no Gemini/Groq configured (optional beans absent)
        ReflectionTestUtils.setField(llmProcess, "geminiClient", null);
        ReflectionTestUtils.setField(llmProcess, "groqClient",   null);

        assertThatThrownBy(() -> llmProcess.extractAlert("plain text"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mockClient, never()).call(anyString());
    }

    @Test
    void promptEngineIsCalledOnce() {
        when(geminiClient.call(anyString()))
                .thenReturn("{\"alertName\":\"A\",\"severity\":\"LOW\"}");

        llmProcess.extractAlert("input");

        verify(promptEngine, times(1)).build("input");
    }
}
