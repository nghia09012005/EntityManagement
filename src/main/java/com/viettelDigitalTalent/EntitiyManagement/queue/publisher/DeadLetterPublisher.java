package com.viettelDigitalTalent.EntitiyManagement.queue.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants.DEAD_LETTER_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String sourceTopic, String originalPayload, Exception ex) {
        Map<String, Object> dlqMessage = Map.of(
            "sourceTopic",     sourceTopic,
            "originalPayload", originalPayload != null ? originalPayload : "",
            "error",           ex.getMessage() != null ? ex.getMessage() : "unknown",
            "errorClass",      ex.getClass().getSimpleName()
        );
        try {
            kafkaTemplate.send(DEAD_LETTER_QUEUE, objectMapper.writeValueAsString(dlqMessage));
            log.warn("[DLQ] Published failed event from topic '{}': {}", sourceTopic, ex.getMessage());
        } catch (JsonProcessingException e) {
            log.error("[DLQ] Failed to serialize DLQ message for topic '{}'", sourceTopic, e);
        }
    }
}
