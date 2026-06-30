package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.DlqEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqWorker {

    private final DlqEventRepository dlqEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = KafkaTopicConstants.DEAD_LETTER_QUEUE, groupId = "soc-dlq-group")
    public void consume(ConsumerRecord<String, String> record) {
        String raw = record.value();
        try {
            Map<String, Object> msg = objectMapper.readValue(raw, new TypeReference<>() {});

            String sourceTopic      = (String) msg.getOrDefault("sourceTopic", "unknown");
            String originalPayload  = (String) msg.getOrDefault("originalPayload", "");
            String error            = (String) msg.getOrDefault("error", "unknown");
            String errorClass       = (String) msg.getOrDefault("errorClass", "Exception");
            String tenantId         = extractTenantId(msg, originalPayload);

            DlqEvent event = DlqEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .sourceTopic(sourceTopic)
                    .originalPayload(originalPayload)
                    .error(error)
                    .errorClass(errorClass)
                    .failedAt(LocalDateTime.now())
                    .build();

            dlqEventRepository.save(event);

            Counter.builder("soc.dlq.events")
                    .description("Dead letter queue events by source topic")
                    .tag("source_topic", sourceTopic)
                    .register(meterRegistry)
                    .increment();

            log.warn("[DLQ] Stored failed event from '{}': {} — {}", sourceTopic, errorClass, error);

        } catch (Exception e) {
            log.error("[DLQ] Failed to process DLQ message: {}", raw, e);
        }
    }

    private String extractTenantId(Map<String, Object> msg, String payload) {
        Object wrapperTenantId = msg.get("tenantId");
        if (wrapperTenantId instanceof String s && !s.isBlank()) return s;
        if (payload == null || payload.isBlank()) return "default";
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            com.fasterxml.jackson.databind.JsonNode tid = node.get("tenantId");
            if (tid != null && !tid.isNull() && !tid.asText().isBlank()) return tid.asText();
        } catch (Exception ignored) {}
        return "default";
    }
}
