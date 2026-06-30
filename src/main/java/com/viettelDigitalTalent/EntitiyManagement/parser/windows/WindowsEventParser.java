package com.viettelDigitalTalent.EntitiyManagement.parser.windows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class WindowsEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AuthenticationEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            AuthenticationEvent event;

            if (root.has("actor") || root.has("src_endpoint") || root.has("dst_endpoint") || root.has("class_uid")) {
                // Thêm eventType nếu chưa có
                if (!root.has("eventType")) {
                    ((ObjectNode) root).put("eventType", "AUTHENTICATION");
                }
                event = objectMapper.treeToValue(root, AuthenticationEvent.class);
            } else {
                event = new AuthenticationEvent();
                event.setUsername(JsonUtils.extractValue(root, "user", "username", "accountName"));
                event.setIpAddress(JsonUtils.extractValue(root, "ip", "sourceIp", "client_ip", "ipAddress"));
                event.setWorkstation(JsonUtils.extractValue(root, "workstation", "hostname", "host", "computer"));

                boolean success = false;
                if (root.has("is_success")) {
                    success = root.get("is_success").asBoolean();
                } else if (root.has("success")) {
                    success = root.get("success").asBoolean();
                }
                event.setSuccess(success);

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

                event.setCategory(EventCategory.AUTHENTICATION.name());
            }

            // Always populate rawData for downstream workers
            if (event.getUsername() != null) event.getRawData().put("username", event.getUsername());
            if (event.getIpAddress() != null) event.getRawData().put("ipAddress", event.getIpAddress());
            if (event.getWorkstation() != null) event.getRawData().put("workstation", event.getWorkstation());
            event.getRawData().put("success", event.isSuccess());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Windows Log bằng Jackson: " + e.getMessage());
        }
    }
}