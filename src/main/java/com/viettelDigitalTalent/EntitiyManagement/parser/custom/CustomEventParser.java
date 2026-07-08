package com.viettelDigitalTalent.EntitiyManagement.parser.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;

@Component
public class CustomEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public BaseEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);
            if (root == null || !root.isObject()) {
                return null;
            }

            String eventType = root.has("eventType") ? root.get("eventType").asText(null) : null;
            if (eventType == null || eventType.isBlank()) {
                return null;
            }

            AlertEvent event = new AlertEvent();
            event.setAlertName("Custom Event: " + eventType);
            event.setSeverity("LOW");
            event.setDescription(root.toString());
            event.setSource("custom-parser");
            event.setTimestamp(LocalDateTime.now());
            event.setCategory("THREAT");
            event.getRawData().put("eventType", eventType);
            event.getRawData().put("customEventType", eventType);
            event.getRawData().put("customPayload", root.toString());

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                event.getRawData().putIfAbsent(entry.getKey(), scalarValue(entry.getValue()));
            }
            return event;
        } catch (Exception e) {
            return null;
        }
    }

    private Object scalarValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        return node.toString();
    }
}
