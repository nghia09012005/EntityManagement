package com.viettelDigitalTalent.EntitiyManagement.parser.windows;

import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.parser.ParserException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
public class WindowsEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot tự động cấu hình sẵn

    @Override
    public AuthenticationEvent parse(String rawData) {
        try {
            // Đọc rawData thành cây JsonNode
            JsonNode root = objectMapper.readTree(rawData);

            AuthenticationEvent event = new AuthenticationEvent();

            // Sử dụng tiện ích extractValue để lấy dữ liệu an toàn
            // Không sợ bị NullPointerException nếu trường không tồn tại
            event.setUsername(JsonUtils.extractValue(root, "user", "username", "accountName"));
            event.setIpAddress(JsonUtils.extractValue(root, "ip", "sourceIp", "client_ip"));

            // Xử lý kiểu boolean với giá trị mặc định nếu không thấy
            boolean success = root.has("is_success") ? root.get("is_success").asBoolean() : false;
            event.setSuccess(success);

            event.setTimestamp(LocalDateTime.now());
            event.setCategory(EventCategory.AUTHENTICATION); // Gán category chuẩn

            return event;
        } catch (Exception e) {
            // Ném ra exception riêng của hệ thống SOC
            throw new RuntimeException("Lỗi khi parse Windows Log bằng Jackson: " + e.getMessage());
        }
    }
}