package com.viettelDigitalTalent.EntitiyManagement.parser.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AlertEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AlertEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            AlertEvent event = new AlertEvent();

            event.setAlertName(JsonUtils.extractValue(root, "alertName", "alert_name", "name", "title"));
            event.setSeverity(JsonUtils.extractValue(root, "severity", "level"));
            event.setDescription(JsonUtils.extractValue(root, "description", "desc", "message"));
            event.setTargetIp(JsonUtils.extractValue(root, "targetIp", "target_ip", "dest_ip", "destinationIp"));
            event.setTargetUser(JsonUtils.extractValue(root, "targetUser", "target_user", "username", "user"));
            event.setTargetFileHash(JsonUtils.extractValue(root, "targetFileHash", "target_file_hash", "fileHash", "hash", "sha256"));

            event.getRawData().put("alertName", event.getAlertName());
            event.getRawData().put("severity", event.getSeverity());
            event.getRawData().put("description", event.getDescription());
            event.getRawData().put("targetIp", event.getTargetIp());
            event.getRawData().put("targetUser", event.getTargetUser());
            event.getRawData().put("targetFileHash", event.getTargetFileHash());

            event.setTimestamp(LocalDateTime.now());
            event.setCategory(EventCategory.THREAT.name());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Alert Log bằng Jackson: " + e.getMessage());
        }
    }
}
