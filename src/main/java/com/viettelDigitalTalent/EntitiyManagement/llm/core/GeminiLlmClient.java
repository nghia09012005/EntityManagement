package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "gemini", name = "api-key")
public class GeminiLlmClient implements LlmClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    // Danh sách model theo thứ tự ưu tiên — fallback lần lượt khi 503
    private static final String[] MODELS = { "gemini-2.0-flash", "gemini-1.5-flash" };

    @Value("${gemini.api-key}")
    private String apiKey;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestTemplate restTemplate;

    @Override
    public String modelName() { return MODELS[0]; }

    @Override
    public String call(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", 512)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        for (String model : MODELS) {
            String url = BASE + model + ":generateContent";
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    log.warn("[Gemini] {} → HTTP {}, thử model tiếp theo", model, response.getStatusCode());
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                String text = root.path("candidates").path(0)
                                  .path("content").path("parts").path(0)
                                  .path("text").asText(null);

                log.info("[Gemini] {} → thành công", model);
                return text;

            } catch (HttpServerErrorException e) {
                // 503 overloaded — thử model tiếp theo
                log.warn("[Gemini] {} → {} ({}), thử model tiếp theo", model, e.getStatusCode(), e.getMessage().substring(0, Math.min(80, e.getMessage().length())));
            } catch (Exception e) {
                log.error("[Gemini] {} → lỗi: {}", model, e.getMessage());
                return null;
            }
        }

        log.error("[Gemini] Tất cả model đều thất bại.");
        return null;
    }
}
