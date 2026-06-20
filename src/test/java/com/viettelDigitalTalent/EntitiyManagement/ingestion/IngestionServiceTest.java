package com.viettelDigitalTalent.EntitiyManagement.ingestion;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @InjectMocks
    private IngestionService ingestionService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void sendsDataToRawLogsTopic() {
        String logLine = "{\"user\":\"admin\",\"ip\":\"1.2.3.4\",\"is_success\":true}";
        ingestionService.sendToQueue(logLine);
        verify(kafkaTemplate, times(1)).send("raw-logs", logLine);
    }

    @Test
    void sendsAlertEventToRawLogsTopic() {
        String logLine = "{\"eventType\":\"ALERT\",\"alertName\":\"Brute Force\",\"severity\":\"HIGH\"}";
        ingestionService.sendToQueue(logLine);
        verify(kafkaTemplate, times(1)).send("raw-logs", logLine);
    }
}
