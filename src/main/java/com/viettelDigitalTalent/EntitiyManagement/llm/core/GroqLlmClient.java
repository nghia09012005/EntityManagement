package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "groq", name = "api-key")
public class GroqLlmClient implements LlmClient {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    @Value("${groq.api-key}")
    private String apiKey;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestTemplate restTemplate;

    @Override
    public String modelName() { return "groq/" + MODEL; }

    @Override
    public String call(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.1,
                    "max_tokens", 512
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Groq] HTTP {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").path(0)
                       .path("message").path("content").asText(null);

        } catch (Exception e) {
            log.error("[Groq] Lỗi gọi API: {}", e.getMessage());
            return null;
        }
    }
}
