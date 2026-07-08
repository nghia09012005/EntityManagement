package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.alert.AlertEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.network.NetworkEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.process.ProcessEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.custom.CustomEventParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ParserDispatcher {

    @Autowired private ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private WindowsEventParser windowsParser;
    @Autowired private ProcessEventParser processParser;
    @Autowired private AlertEventParser alertParser;
    @Autowired private NetworkEventParser networkParser;
    @Autowired private CustomEventParser customParser;
    @Autowired private LlmProcess llmProcess;

    /**
     * Tự động nhận dạng loại event từ nội dung JSON, không cần biết nguồn file.
     * Nhận được cả flat JSON cũ lẫn OCSF JSON không có eventType bằng class_uid
     * hoặc các object nested đặc trưng.
     * Nếu input là free-text (không phải JSON) → fallback sang LLM để trích xuất AlertEvent.
     */
    public BaseEvent autoDetect(String rawData) {
        // Free-text (không phải JSON) → trả placeholder ngay, LLM sẽ chạy async trong ParserWorker
        String trimmed = rawData.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            log.info("[ParserDispatcher] Input là free-text, tạo placeholder — LLM sẽ enrich async.");
            AlertEvent placeholder = new AlertEvent();
            placeholder.setAlertName("Pending LLM Analysis");
            placeholder.setSeverity("LOW");
            placeholder.setDescription(rawData.length() > 500 ? rawData.substring(0, 500) : rawData);
            placeholder.setSource("free-text");
            placeholder.setCategory(com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory.THREAT.name());
            return placeholder;
        }

        JsonNode root = readDetectionRoot(rawData);

        String eventType = text(root, "eventType");

        if (eventType != null) {
            return switch (eventType.toUpperCase()) {
                case "AUTHENTICATION" -> windowsParser.parse(rawData);
                case "PROCESS"        -> processParser.parse(rawData);
                case "NETWORK"        -> networkParser.parse(rawData);
                case "ALERT", "THREAT", "SECURITY_FINDING" -> alertParser.parse(rawData);
                default -> tryCustomParserOrFallback(rawData);
            };
        }

        int classUid = intValue(root, "class_uid");
        if (classUid > 0) {
            return switch (classUid) {
                case 3002 -> windowsParser.parse(rawData);
                case 1007 -> processParser.parse(rawData);
                case 4001 -> networkParser.parse(rawData);
                case 2001 -> alertParser.parse(rawData);
                default -> throw unknown(rawData);
            };
        }

        if (hasAny(root, "finding", "cve_uid", "targetCve", "target_url", "cloud_resource_id", "target_email")) {
            return alertParser.parse(rawData);
        }
        if (root.has("process")) {
            return processParser.parse(rawData);
        }
        if (looksLikeOcsfAuthentication(root)) {
            return windowsParser.parse(rawData);
        }
        if (looksLikeOcsfNetwork(root)) {
            return networkParser.parse(rawData);
        }

        if (hasAny(root, "processName", "commandLine", "parentProcess")) {
            return processParser.parse(rawData);
        }
        if (hasAny(root, "srcIp", "dstIp")) {
            return networkParser.parse(rawData);
        }
        if (hasAny(root, "alertName", "severity")) {
            return alertParser.parse(rawData);
        }
        if (hasAny(root, "username", "workstation", "accountName", "user")) {
            return windowsParser.parse(rawData);
        }

        return tryCustomParserOrFallback(rawData);
    }

    private BaseEvent tryCustomParserOrFallback(String rawData) {
        try {
            BaseEvent customEvent = customParser.parse(rawData);
            if (customEvent != null) {
                log.info("[ParserDispatcher] Custom parser handled unknown payload");
                return customEvent;
            }
        } catch (Exception e) {
            log.warn("[ParserDispatcher] Custom parser failed, falling back to LLM: {}", e.getMessage());
        }

        if (llmProcess != null) {
            AlertEvent llmFallback = new AlertEvent();
            llmFallback.setAlertName("Pending LLM Analysis");
            llmFallback.setSeverity("LOW");
            llmFallback.setDescription(rawData.length() > 500 ? rawData.substring(0, 500) : rawData);
            llmFallback.setSource("llm-fallback");
            llmFallback.setCategory(com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory.THREAT.name());
            log.info("[ParserDispatcher] Falling back to LLM placeholder for unknown payload");
            return llmFallback;
        }

        throw unknown(rawData);
    }

    /** Kept for backward-compat nếu có code khác gọi. */
    public BaseEvent parse(String source, String rawData) {
        return switch (source.toLowerCase()) {
            case "windows" -> windowsParser.parse(rawData);
            case "process" -> processParser.parse(rawData);
            case "alert"   -> alertParser.parse(rawData);
            case "network" -> networkParser.parse(rawData);
            default        -> autoDetect(rawData);
        };
    }

    private JsonNode readDetectionRoot(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            if (root != null && root.isArray() && !root.isEmpty()) {
                return root.get(0);
            }
            return root;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown log format: invalid JSON", e);
        }
    }

    private boolean looksLikeOcsfAuthentication(JsonNode root) {
        return root.has("actor")
                && (hasPath(root, "src_endpoint.ip") || hasPath(root, "dst_endpoint.hostname"))
                && (root.has("status") || root.has("status_id"));
    }

    private boolean looksLikeOcsfNetwork(JsonNode root) {
        return root.has("src_endpoint")
                && root.has("dst_endpoint")
                && hasPath(root, "src_endpoint.ip")
                && (hasPath(root, "dst_endpoint.ip")
                    || hasPath(root, "dst_endpoint.domain")
                    || hasPath(root, "dst_endpoint.port"));
    }

    private IllegalArgumentException unknown(String rawData) {
        log.warn("[ParserDispatcher] Không nhận dạng được định dạng log, đẩy vào DLQ. Preview: {}",
                rawData.length() > 120 ? rawData.substring(0, 120) + "…" : rawData);
        return new IllegalArgumentException("Unknown log format: no recognizable fields found");
    }

    private boolean hasAny(JsonNode root, String... fields) {
        for (String field : fields) {
            if (root.has(field)) return true;
        }
        return false;
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private int intValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.canConvertToInt() ? node.asInt() : 0;
    }

    private boolean hasPath(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isNull()) return false;
            current = current.get(part);
        }
        return current != null && !current.isNull() && !current.asText("").isBlank();
    }
}
