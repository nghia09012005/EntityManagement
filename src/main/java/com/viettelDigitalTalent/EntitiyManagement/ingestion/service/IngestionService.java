package com.viettelDigitalTalent.EntitiyManagement.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.dto.IngestionEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendToQueue(String rawData, String tenantId) {
        try {
            IngestionEnvelope envelope = new IngestionEnvelope(tenantId, rawData);
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("raw-logs", json);
        } catch (Exception e) {
            log.error("[IngestionService] Lỗi khi gửi log vào queue", e);
        }
    }
}
