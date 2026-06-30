package com.viettelDigitalTalent.EntitiyManagement.parser.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ProcessEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ProcessEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            ProcessEvent event;

            if (root.has("process") || root.has("class_uid")) {
                // Thêm eventType nếu chưa có
                if (!root.has("eventType")) {
                    ((ObjectNode) root).put("eventType", "PROCESS");
                }
                event = objectMapper.treeToValue(root, ProcessEvent.class);
            } else {
                event = new ProcessEvent();
                event.setProcessName(JsonUtils.extractValue(root, "processName", "process_name", "name"));
                event.setProcessPath(JsonUtils.extractValue(root, "processPath", "process_path", "path"));
                event.setFileHash(JsonUtils.extractValue(root, "fileHash", "file_hash", "sha256", "md5"));
                event.setCommandLine(JsonUtils.extractValue(root, "commandLine", "command_line", "cmd"));

                // Parse ISO 8601 timestamp if provided, otherwise use now
                String tsRaw = JsonUtils.extractValue(root, "timestamp", "time", "eventTime");
                if (tsRaw != null) {
                    try {
                        event.setTimestamp(LocalDateTime.ofInstant(Instant.parse(tsRaw), ZoneId.systemDefault()));
                    } catch (Exception ignored) {
                        event.setTimestamp(LocalDateTime.now());
                    }
                } else {
                    event.setTimestamp(LocalDateTime.now());
                }

                event.setCategory(EventCategory.PROCESS.name());
            }

            // Always populate rawData for downstream workers
            event.getRawData().put("processName", event.getProcessName());
            event.getRawData().put("processPath", event.getProcessPath());
            event.getRawData().put("fileHash", event.getFileHash());
            event.getRawData().put("commandLine", event.getCommandLine());

            // Extract hostname from raw log if present and put in rawData
            String hostname = JsonUtils.extractValue(root, "hostname", "host", "machine", "computer");
            if (hostname != null) {
                event.getRawData().put("hostname", hostname);
            } else if (event.getRawData().get("hostname") == null) {
                // If it was parsed as nested OCSF, try to read from deserialized raw_data
                if (root.has("raw_data") && root.get("raw_data").has("hostname")) {
                    event.getRawData().put("hostname", root.get("raw_data").get("hostname").asText());
                }
            }

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Process Log bằng Jackson: " + e.getMessage());
        }
    }
}