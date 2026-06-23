package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ParserWorker {

    @Autowired private ParserDispatcher parserDispatcher;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TaskExecutor taskExecutor;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private LlmProcess llmProcess;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private DeadLetterPublisher deadLetterPublisher;

    private Counter eventsProcessedCounter() {
        return Counter.builder("soc.events.processed")
                .description("Total events processed by ParserWorker")
                .register(meterRegistry);
    }

    @KafkaListener(topics = KafkaTopicConstants.RAW_LOGS, groupId = "soc-parser-group")
    public void consume(ConsumerRecord<String, String> record) {
        String source  = record.key();
        String rawJson = record.value();

        log.info("DEBUG Kafka Raw: {}", rawJson);

        try {
            BaseEvent event = resolveEvent(rawJson);

            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            log.info("Parsed event: {} [ID: {}]", event.getClass().getSimpleName(), event.getEventId());
            eventsProcessedCounter().increment();

            String eventId       = event.getEventId();
            String eventSource   = source != null ? source : event.getSource();
            String eventCategory = event.getCategory();
            LocalDateTime timestamp = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();

            Map<String, Object> rawDataSnapshot;
            try {
                rawDataSnapshot = objectMapper.readValue(rawJson, new TypeReference<>() {});
            } catch (Exception ex) {
                rawDataSnapshot = new HashMap<>();
                rawDataSnapshot.put("_raw", rawJson);
            }

            // Sync: lưu raw log ngay trên Kafka consumer thread — đảm bảo document
            // tồn tại trước khi EnrichmentWorker chạy updateEnrichment.
            try {
                auditLogRepository.saveRawLog(eventId, eventSource, eventCategory, timestamp, rawDataSnapshot);
                log.info("[ParserWorker] Lưu raw log thành công cho ID: {}", eventId);
            } catch (Exception e) {
                log.error("[ParserWorker] Lỗi khi lưu raw log cho ID: {}", eventId, e);
            }

            boolean isFreeText = !rawJson.trim().startsWith("{") && !rawJson.trim().startsWith("[");

            if (isFreeText && event instanceof AlertEvent alertEvent) {
                // Free-text: chạy LLM async rồi mới publish normalized-events
                CompletableFuture.runAsync(() -> {
                    try {
                        AlertEvent llmResult = llmProcess.extractAlert(rawJson);
                        if (llmResult != null) {
                            if (llmResult.getAlertName()   != null) alertEvent.setAlertName(llmResult.getAlertName());
                            if (llmResult.getSeverity()    != null) alertEvent.setSeverity(llmResult.getSeverity());
                            if (llmResult.getDescription() != null) alertEvent.setDescription(llmResult.getDescription());
                            alertEvent.setTargetIp(llmResult.getTargetIp());
                            alertEvent.setTargetUser(llmResult.getTargetUser());
                            alertEvent.setTargetHost(llmResult.getTargetHost());
                            alertEvent.setTargetDomain(llmResult.getTargetDomain());
                            alertEvent.setTargetFileHash(llmResult.getTargetFileHash());
                        }
                        publishNormalized(alertEvent, rawJson);
                    } catch (Exception e) {
                        log.error("[ParserWorker] Lỗi LLM cho ID: {}", eventId, e);
                        deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, rawJson, e);
                    }
                }, taskExecutor);
            } else {
                publishNormalized(event, rawJson);
            }

        } catch (Exception e) {
            log.error("Lỗi parse log từ {}: {}", source, e.getMessage());
            deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, rawJson, e);
        }
    }

    private void publishNormalized(BaseEvent event, String originalRaw) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopicConstants.NORMALIZED_EVENTS, payload);
            log.info("[ParserWorker] Published to normalized-events, ID: {}", event.getEventId());
        } catch (Exception e) {
            log.error("[ParserWorker] Lỗi publish normalized event ID: {}", event.getEventId(), e);
            deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, originalRaw, e);
        }
    }

    private BaseEvent resolveEvent(String rawJson) {
        try {
            BaseEvent event = objectMapper.readValue(rawJson, BaseEvent.class);

            if (event instanceof AuthenticationEvent) {
                event.setCategory(EventCategory.AUTHENTICATION.name());
            } else if (event instanceof ProcessEvent) {
                event.setCategory(EventCategory.PROCESS.name());
            } else if (event instanceof NetworkEvent) {
                event.setCategory(EventCategory.NETWORK.name());
            } else if (event instanceof AlertEvent) {
                event.setCategory(EventCategory.THREAT.name());
            }

            return event;
        } catch (Exception ignored) {
            log.warn("Auto detect");
            return parserDispatcher.autoDetect(rawJson);
        }
    }
}
