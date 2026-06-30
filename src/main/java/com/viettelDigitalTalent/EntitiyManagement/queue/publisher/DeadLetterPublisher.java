package com.viettelDigitalTalent.EntitiyManagement.queue.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants.DEAD_LETTER_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String sourceTopic, String originalPayload, Exception ex) {
        publish(sourceTopic, originalPayload, null, ex);
    }

    public void publish(String sourceTopic, String originalPayload, String tenantId, Exception ex) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("sourceTopic", sourceTopic);
        dlqMessage.put("originalPayload", originalPayload != null ? originalPayload : "");
        dlqMessage.put("error", ex.getMessage() != null ? ex.getMessage() : "unknown");
        dlqMessage.put("errorClass", ex.getClass().getSimpleName());
        if (tenantId != null && !tenantId.isBlank()) {
            dlqMessage.put("tenantId", tenantId);
        }
        try {
            kafkaTemplate.send(DEAD_LETTER_QUEUE, objectMapper.writeValueAsString(dlqMessage));
            log.warn("[DLQ] Published failed event from topic '{}': {}", sourceTopic, ex.getMessage());
        } catch (JsonProcessingException e) {
            log.error("[DLQ] Failed to serialize DLQ message for topic '{}'", sourceTopic, e);
        }
    }
}
