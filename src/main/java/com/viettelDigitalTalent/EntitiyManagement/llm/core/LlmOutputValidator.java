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
    private static final String DEFAULT_FINDING_TYPE = "security_finding";

    @Autowired private ObjectMapper objectMapper;

    /**
     * Parse JSON text từ LLM response → OCSF Security Finding AlertEvent.
     * Trả về null nếu output không hợp lệ.
     */
    public AlertEvent validate(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return null;

        try {

            log.warn("[LlmValidator] output llm: {}",llmOutput);



            // Strip markdown code blocks nếu model trả về ```json ... ```
            String json = llmOutput.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)```[a-z]*\\s*", "").replace("```", "").trim();
            }

            JsonNode node = objectMapper.readTree(json);

            String message = node.path("message").asText();
            if ("Unknown log line".equals(message)) {
                log.warn("[LlmValidator] output báo hiệu log không xác định, bỏ qua.");
                return null;
            }


            String alertName = firstText(node, "finding.title", "alertName");
            if (alertName == null || alertName.isBlank()) {
                log.warn("[LlmValidator] Thiếu finding.title/alertName, bỏ qua.");
                return null;
            }

            String severity = text(node, "severity");
            if (severity == null || !VALID_SEVERITIES.contains(severity.toUpperCase())) {
                severity = "MEDIUM";
            }

            AlertEvent event = new AlertEvent();
            event.setEventId(text(node, "uid"));
            event.setActivityId(intValue(node, "activity_id", 1));
            event.setTime(longValue(node, "time", 0L));
            event.setAlertName(alertName);
            event.setSeverity(severity.toUpperCase());
            event.setDescription(firstText(node, "finding.desc", "description", "message"));
            event.setTargetIp(firstText(node, "dst_endpoint.ip", "targetIp"));
            //srcIP
            event.setSourceIp(firstText(node, "src_endpoint.ip", "sourceIp"));
            event.setSourceHost(firstText(node, "src_endpoint.hostname", "sourceHost"));
            event.setSourceDomain(firstText(node, "src_endpoint.domain", "sourceDomain"));
            //---------
            event.setTargetUser(firstText(node, "actor.user.name", "targetUser"));
            event.setTargetHost(firstText(node, "dst_endpoint.hostname", "targetHost"));
            event.setTargetDomain(firstText(node, "dst_endpoint.domain", "targetDomain"));
            event.setTargetFileHash(firstText(node, "process.file.hashes.0.value", "targetFileHash"));
            event.setTargetUrl(firstText(node, "target_url", "targetUrl"));
            event.setTargetCloudResourceId(firstText(node, "cloud_resource_id", "targetCloudResourceId"));
            event.setTargetEmail(firstText(node, "target_email", "targetEmail"));
            event.setTargetCve(firstText(node, "cve_uid", "targetCve"));
            event.setSource(text(node, "source"));
            event.setCategory(EventCategory.THREAT.name());
            if (event.getFinding() != null && event.getFinding().getTypes() == null) {
                event.getFinding().setTypes(java.util.List.of(DEFAULT_FINDING_TYPE));
            }

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

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            String value = textAt(node, path);
            if (value != null) return value;
        }
        return null;
    }

    private String textAt(JsonNode node, String path) {
        JsonNode cur = node;
        for (String part : path.split("\\.")) {
            if (cur == null || cur.isNull()) return null;
            if (cur.isArray()) {
                try {
                    cur = cur.get(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                cur = cur.get(part);
            }
        }
        if (cur == null || cur.isNull()) return null;
        String v = cur.asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode n = node.get(field);
        return n != null && n.canConvertToInt() ? n.asInt() : fallback;
    }

    private long longValue(JsonNode node, String field, long fallback) {
        JsonNode n = node.get(field);
        return n != null && n.canConvertToLong() ? n.asLong() : fallback;
    }
}
