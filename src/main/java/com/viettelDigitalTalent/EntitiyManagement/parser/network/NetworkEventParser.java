package com.viettelDigitalTalent.EntitiyManagement.parser.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class NetworkEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public NetworkEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            NetworkEvent event;

            if (root.has("src_endpoint") || root.has("dst_endpoint") || root.has("class_uid")) {
                // Thêm eventType nếu chưa có
                if (!root.has("eventType")) {
                    ((ObjectNode) root).put("eventType", "NETWORK");
                }
                event = objectMapper.treeToValue(root, NetworkEvent.class);
            } else {
                event = new NetworkEvent();
                event.setSrcIp(JsonUtils.extractValue(root, "srcIp", "src_ip", "sourceIp", "source"));
                event.setDstIp(JsonUtils.extractValue(root, "dstIp", "dst_ip", "destinationIp", "destination"));
                event.setDstDomain(JsonUtils.extractValue(root, "dstDomain", "dst_domain", "domain", "hostname"));

                String portStr = JsonUtils.extractValue(root, "dstPort", "dst_port", "port");
                int port = 0;
                try {
                    if (portStr != null && !"UNKNOWN".equals(portStr)) {
                        port = Integer.parseInt(portStr);
                    }
                } catch (NumberFormatException ignored) {}
                event.setDstPort(port);

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

                event.setCategory(EventCategory.NETWORK.name());
            }

            // Always populate rawData for downstream workers
            event.getRawData().put("srcIp", event.getSrcIp());
            event.getRawData().put("dstIp", event.getDstIp());
            event.getRawData().put("dstDomain", event.getDstDomain());
            event.getRawData().put("dstPort", event.getDstPort());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Network Log bằng Jackson: " + e.getMessage());
        }
    }
}
