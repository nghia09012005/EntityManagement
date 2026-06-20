package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class LlmOutputValidator {

    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    @Autowired private ObjectMapper objectMapper;

    /**
     * Parse JSON text từ LLM response → AlertEvent.
     * Trả về null nếu output không hợp lệ.
     */
    public AlertEvent validate(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;

        try {
            // Strip markdown code blocks nếu model trả về ```json ... ```
            String json = llmOutput.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)```[a-z]*\\s*", "").replace("```", "").trim();
            }

            JsonNode node = objectMapper.readTree(json);

            String alertName = text(node, "alertName");
            if (alertName == null || alertName.isBlank()) {
                log.warn("[LlmValidator] Thiếu alertName, bỏ qua.");
                return null;
            }

            String severity = text(node, "severity");
            if (severity == null || !VALID_SEVERITIES.contains(severity.toUpperCase())) {
                severity = "MEDIUM";
            }

            AlertEvent event = new AlertEvent();
            event.setAlertName(alertName);
            event.setSeverity(severity.toUpperCase());
            event.setDescription(text(node, "description"));
            event.setTargetIp(text(node, "targetIp"));
            event.setTargetUser(text(node, "targetUser"));
            event.setTargetHost(text(node, "targetHost"));
            event.setTargetDomain(text(node, "targetDomain"));
            event.setTargetFileHash(text(node, "targetFileHash"));
            event.setSource(text(node, "source"));
            event.setCategory(EventCategory.THREAT.name());

            return event;

        } catch (Exception e) {
            log.warn("[LlmValidator] Lỗi parse JSON từ LLM: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String v = n.asText("").trim();
        return v.isEmpty() ? null : v;
    }
}
