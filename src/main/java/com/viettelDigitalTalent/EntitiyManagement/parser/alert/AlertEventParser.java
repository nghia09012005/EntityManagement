package com.viettelDigitalTalent.EntitiyManagement.parser.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            AlertEvent event;

            if (root.has("finding") || root.has("actor") || root.has("process") || root.has("dst_endpoint") || root.has("class_uid")) {
                // Thêm eventType nếu chưa có
                if (!root.has("eventType")) {
                    ((ObjectNode) root).put("eventType", "THREAT");
                }
                event = objectMapper.treeToValue(root, AlertEvent.class);
            } else {
                event = new AlertEvent();
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

                String targetUrl = JsonUtils.extractValue(root, "targetUrl", "target_url", "url");
                if (targetUrl != null) event.setTargetUrl(targetUrl);

                String targetCloudResourceId = JsonUtils.extractValue(root, "targetCloudResourceId", "cloud_resource_id", "cloudResourceId");
                if (targetCloudResourceId != null) event.setTargetCloudResourceId(targetCloudResourceId);

                String targetEmail = JsonUtils.extractValue(root, "targetEmail", "target_email", "email");
                if (targetEmail != null) event.setTargetEmail(targetEmail);

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

                event.setCategory(EventCategory.THREAT.name());
            }

            // Always populate rawData for downstream workers
            if (event.getAlertName() != null) event.getRawData().put("alertName", event.getAlertName());
            if (event.getSeverity() != null) event.getRawData().put("severity", event.getSeverity());
            if (event.getDescription() != null) event.getRawData().put("description", event.getDescription());
            if (event.getTargetIp() != null) event.getRawData().put("targetIp", event.getTargetIp());
            if (event.getTargetUser() != null) event.getRawData().put("targetUser", event.getTargetUser());
            if (event.getTargetHost() != null) event.getRawData().put("targetHost", event.getTargetHost());
            if (event.getTargetDomain() != null) event.getRawData().put("targetDomain", event.getTargetDomain());
            if (event.getTargetFileHash() != null) event.getRawData().put("targetFileHash", event.getTargetFileHash());
            if (event.getTargetProcess() != null) event.getRawData().put("targetProcess", event.getTargetProcess());
            if (event.getTargetUrl() != null) event.getRawData().put("targetUrl", event.getTargetUrl());
            if (event.getTargetCloudResourceId() != null) event.getRawData().put("targetCloudResourceId", event.getTargetCloudResourceId());
            if (event.getTargetEmail() != null) event.getRawData().put("targetEmail", event.getTargetEmail());
            if (event.getTargetCve() != null) event.getRawData().put("targetCve", event.getTargetCve());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Alert Log bằng Jackson: " + e.getMessage());
        }
    }
}
