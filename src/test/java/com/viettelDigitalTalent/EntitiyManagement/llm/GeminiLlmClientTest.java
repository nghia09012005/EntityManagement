package com.viettelDigitalTalent.EntitiyManagement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.GeminiLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiLlmClientTest {

    private GeminiLlmClient client;

    @Mock RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        client = new GeminiLlmClient();
        ReflectionTestUtils.setField(client, "apiKey",       "test-gemini-key");
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
    }

    @Test
    void modelNameReturnsFirstModel() {
        assertThat(client.modelName()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void callSuccessReturnsText() throws Exception {
        String jsonBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello output\"}]}}]}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonBody));

        String result = client.call("test prompt");

        assertThat(result).isEqualTo("hello output");
    }

    @Test
    void callFallsBackToNextModelOn503() throws Exception {
        // First model (gemini-2.0-flash) throws 503
        when(restTemplate.postForEntity(contains("gemini-2.0-flash"), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"));
        // Second model (gemini-1.5-flash) succeeds
        String jsonBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"fallback output\"}]}}]}";
        when(restTemplate.postForEntity(contains("gemini-1.5-flash"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonBody));

        String result = client.call("prompt");

        assertThat(result).isEqualTo("fallback output");
    }

    @Test
    void callReturnsNullWhenAllModelsFail() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "overloaded"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullOnNonServerError() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullWhenResponseBodyNull() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok((String) null));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullWhenResponseNotSuccessful() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        assertThat(client.call("prompt")).isNull();
    }

    @Test
    void callReturnsNullOnMalformedJson() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("not-valid-json"));

        assertThat(client.call("prompt")).isNull();
    }
}
