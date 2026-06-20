package com.viettelDigitalTalent.EntitiyManagement.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsFirstMatchingKey() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("username", "admin");
        assertThat(JsonUtils.extractValue(node, "user", "username")).isEqualTo("admin");
    }

    @Test
    void returnsFirstKeyWhenMultiplePresent() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("user", "alice");
        node.put("username", "bob");
        assertThat(JsonUtils.extractValue(node, "user", "username")).isEqualTo("alice");
    }

    @Test
    void returnsNullWhenNoKeyMatches() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("ip", "1.2.3.4");
        assertThat(JsonUtils.extractValue(node, "user", "username")).isNull();
    }

    @Test
    void returnsNullForExplicitNullField() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.putNull("user");
        assertThat(JsonUtils.extractValue(node, "user")).isNull();
    }

    @Test
    void returnsValueFromSecondKeyWhenFirstNull() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.putNull("user");
        node.put("username", "carol");
        assertThat(JsonUtils.extractValue(node, "user", "username")).isEqualTo("carol");
    }
}
