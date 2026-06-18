package com.viettelDigitalTalent.EntitiyManagement.common.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {
    public static String extractValue(JsonNode node, String... possibleKeys) {
        for (String key : possibleKeys) {
            JsonNode field = node.get(key);
            if (field != null && !field.isNull()) {
                return field.asText();
            }
        }
        return null;
    }
}