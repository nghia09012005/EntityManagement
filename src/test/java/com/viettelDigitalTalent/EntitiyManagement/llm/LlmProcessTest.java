package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GeminiLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GroqLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmOutputValidator;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmPromptEngine;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.MockLlmClient;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
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
    void fallsBackToMockWhenBothGeminiAndGroqFail() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenReturn(null);
        when(mockClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Mock Alert\",\"severity\":\"LOW\"}");

        AlertEvent result = llmProcess.extractAlert("text");

        assertThat(result.getAlertName()).isEqualTo("Mock Alert");
    }

    @Test
    void returnsNullWhenAllClientsFail() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenReturn(null);
        when(mockClient.call(anyString())).thenReturn(null);

        assertThat(llmProcess.extractAlert("text")).isNull();
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
    void handlesGroqExceptionAndFallsBackToMock() {
        when(geminiClient.call(anyString())).thenReturn(null);
        when(groqClient.call(anyString())).thenThrow(new RuntimeException("groq down"));
        when(mockClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Mock\",\"severity\":\"LOW\"}");

        AlertEvent result = llmProcess.extractAlert("text");

        assertThat(result.getAlertName()).isEqualTo("Mock");
    }

    @Test
    void skipsNullClientsGracefully() {
        // Simulate no Gemini/Groq configured (optional beans absent)
        ReflectionTestUtils.setField(llmProcess, "geminiClient", null);
        ReflectionTestUtils.setField(llmProcess, "groqClient",   null);
        when(mockClient.call(anyString()))
                .thenReturn("{\"alertName\":\"Fallback\",\"severity\":\"LOW\"}");

        AlertEvent result = llmProcess.extractAlert("plain text");

        assertThat(result.getAlertName()).isEqualTo("Fallback");
    }

    @Test
    void promptEngineIsCalledOnce() {
        when(geminiClient.call(anyString()))
                .thenReturn("{\"alertName\":\"A\",\"severity\":\"LOW\"}");

        llmProcess.extractAlert("input");

        verify(promptEngine, times(1)).build("input");
    }
}
