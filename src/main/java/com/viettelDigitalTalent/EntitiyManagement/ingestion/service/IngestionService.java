package com.viettelDigitalTalent.EntitiyManagement.ingestion.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendToQueue(String source, String data) {
        // Gửi vào topic raw-logs
        kafkaTemplate.send("raw-logs", source, data);
    }
}
