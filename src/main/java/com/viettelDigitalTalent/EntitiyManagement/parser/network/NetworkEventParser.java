package com.viettelDigitalTalent.EntitiyManagement.parser.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NetworkEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public NetworkEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            NetworkEvent event = new NetworkEvent();

            event.setSrcIp(JsonUtils.extractValue(root, "srcIp", "src_ip", "sourceIp", "source"));
            event.setDstIp(JsonUtils.extractValue(root, "dstIp", "dst_ip", "destinationIp", "destination"));
            event.setDstDomain(JsonUtils.extractValue(root, "dstDomain", "dst_domain", "domain", "hostname"));

            String portStr = JsonUtils.extractValue(root, "dstPort", "dst_port", "port");
            int port = 0;
            try {
                if (!"UNKNOWN".equals(portStr)) {
                    port = Integer.parseInt(portStr);
                }
            } catch (NumberFormatException ignored) {}
            event.setDstPort(port);

            event.getRawData().put("srcIp", event.getSrcIp());
            event.getRawData().put("dstIp", event.getDstIp());
            event.getRawData().put("dstDomain", event.getDstDomain());
            event.getRawData().put("dstPort", event.getDstPort());

            event.setTimestamp(LocalDateTime.now());
            event.setCategory(EventCategory.NETWORK.name());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Network Log bằng Jackson: " + e.getMessage());
        }
    }
}
