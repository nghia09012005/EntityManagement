package com.viettelDigitalTalent.EntitiyManagement.parser.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
            event.setTargetHost(JsonUtils.extractValue(root, "targetHost", "target_host", "hostname", "host"));
            event.setTargetDomain(JsonUtils.extractValue(root, "targetDomain", "target_domain", "domain"));
            event.setTargetFileHash(JsonUtils.extractValue(root, "targetFileHash", "target_file_hash", "fileHash", "hash", "sha256"));
            event.setTargetProcess(JsonUtils.extractValue(root, "targetProcess", "target_process", "processName", "process"));

            String cve = JsonUtils.extractValue(root, "targetCve", "cve_uid", "cve", "cveId");
            if (cve != null) event.setTargetCve(cve);

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

            // Populate rawData for downstream enrichment worker
            if (event.getAlertName()     != null) event.getRawData().put("alertName",     event.getAlertName());
            if (event.getSeverity()      != null) event.getRawData().put("severity",      event.getSeverity());
            if (event.getDescription()   != null) event.getRawData().put("description",   event.getDescription());
            if (event.getTargetIp()      != null) event.getRawData().put("targetIp",      event.getTargetIp());
            if (event.getTargetUser()    != null) event.getRawData().put("targetUser",    event.getTargetUser());
            if (event.getTargetFileHash()!= null) event.getRawData().put("targetFileHash",event.getTargetFileHash());

            event.setCategory(EventCategory.THREAT.name());
            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Alert Log bằng Jackson: " + e.getMessage());
        }
    }
}
