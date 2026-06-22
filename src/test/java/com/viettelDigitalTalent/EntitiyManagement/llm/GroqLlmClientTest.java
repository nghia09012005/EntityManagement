package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GroqLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroqLlmClientTest {

    private GroqLlmClient client;

    @Mock RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        client = new GroqLlmClient();
        ReflectionTestUtils.setField(client, "apiKey",       "test-groq-key");
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
    }

    @Test
    void modelNameContainsGroq() {
        assertThat(client.modelName()).contains("groq");
    }

    @Test
    void callSuccessReturnsContent() {
        String jsonBody = "{\"choices\":[{\"message\":{\"content\":\"groq response\"}}]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonBody));

        assertThat(client.call("prompt")).isEqualTo("groq response");
    }

    @Test
    void callReturnsNullOnHttpError() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("rate limit"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullOnNullBody() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullOnException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullOnMalformedJson() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("not-json"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullWhenChoicesEmpty() {
        String jsonBody = "{\"choices\":[]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonBody));

        // path("choices").path(0).path("message").path("content").asText(null) → null
        assertThat(client.call("prompt")).isNull();
    }
}
