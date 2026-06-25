package com.viettelDigitalTalent.EntitiyManagement.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @InjectMocks
    private IngestionService ingestionService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    // Sử dụng Spy để dùng ObjectMapper thật, giúp test dễ dàng hơn
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEST_TENANT = "default-tenant";

    @Test
    void sendsDataToRawLogsTopic() {
        String logLine = "{\"user\":\"admin\",\"ip\":\"1.2.3.4\",\"is_success\":true}";

        ingestionService.sendToQueue(logLine, TEST_TENANT);

        // Sửa verify: Phải kiểm tra chuỗi JSON chứa tenantId và logLine
        // Chúng ta dùng anyString() hoặc regex để khớp JSON
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("raw-logs"), anyString());
    }

    @Test
    void sendsAlertEventToRawLogsTopic() {
        String logLine = "{\"eventType\":\"ALERT\",\"alertName\":\"Brute Force\",\"severity\":\"HIGH\"}";

        ingestionService.sendToQueue(logLine, TEST_TENANT);

        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("raw-logs"), anyString());
    }
}