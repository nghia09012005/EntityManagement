package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphEntityService;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;

@Slf4j
@Service
public class ParserWorker {

    @Autowired
    private ParserDispatcher parserDispatcher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskExecutor taskExecutor;

    @Autowired
    private EnrichmentService enrichService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private GraphEntityService graphEntityService;

    // Lắng nghe topic 'raw-logs'
    @KafkaListener(topics = "raw-logs", groupId = "soc-parser-group")
    public void consume(ConsumerRecord<String, String> record) {
        String source = record.key(); // Source là key của Kafka message
        String rawData = record.value();

        try {
            BaseEvent event = resolveEvent(source, rawData);

            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            log.info("Parsed event: " + event.getClass().getSimpleName() + " [ID: " + event.getEventId() + "]");

            String eventId = event.getEventId();
            String eventSource = source != null ? source : event.getSource();
            String eventCategory = event.getCategory();
            java.time.LocalDateTime timestamp = event.getTimestamp() != null ? event.getTimestamp() : java.time.LocalDateTime.now();
            Map<String, Object> rawDataSnapshot = new HashMap<>(event.getRawData());

            // 1. Thread 1: Lưu Audit Log thô (Async)
            CompletableFuture.runAsync(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    log.info("[" + threadName + "] Bắt đầu lưu raw log vào MongoDB cho ID: " + eventId);
                    auditLogRepository.saveRawLog(eventId, eventSource, eventCategory, timestamp, rawDataSnapshot);
                    log.info("[" + threadName + "] Lưu raw log thành công cho ID: " + eventId);
                } catch (Exception e) {
                    log.error("[" + threadName + "] Lỗi khi lưu raw log cho ID: " + eventId, e);
                }
            }, taskExecutor);

            // 2. Thread 2: Làm giàu dữ liệu & Cập nhật (Async)
            // 3. Thread 3: Lưu entity graph vào Neo4j (chạy sau Thread 2)
            CompletableFuture.runAsync(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    log.info("[" + threadName + "] Bắt đầu enrich dữ liệu cho ID: " + eventId);
                    enrichService.enrich(event);

                    Map<String, Object> enrichmentData = new HashMap<>();
                    if (event.getRawData().containsKey("geo")) {
                        enrichmentData.put("geo", event.getRawData().get("geo"));
                    }
                    if (event.getRawData().containsKey("malware")) {
                        enrichmentData.put("malware", event.getRawData().get("malware"));
                    }
                    if (event.getRawData().containsKey("srcGeo")) {
                        enrichmentData.put("srcGeo", event.getRawData().get("srcGeo"));
                    }
                    if (event.getRawData().containsKey("dstGeo")) {
                        enrichmentData.put("dstGeo", event.getRawData().get("dstGeo"));
                    }

                    log.info("[" + threadName + "] Bắt đầu cập nhật kết quả enrich vào MongoDB cho ID: " + eventId);
                    auditLogRepository.updateEnrichment(eventId, enrichmentData);
                    log.info("[" + threadName + "] Cập nhật kết quả enrich thành công cho ID: " + eventId);
                } catch (Exception e) {
                    log.error("[" + threadName + "] Lỗi khi làm giàu hoặc cập nhật cho ID: " + eventId, e);
                }
            }, taskExecutor).thenRunAsync(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    log.info("[" + threadName + "] Bắt đầu lưu graph entities cho ID: " + eventId);
                    graphEntityService.save(event);
                    log.info("[" + threadName + "] Lưu graph entities thành công cho ID: " + eventId);
                } catch (Exception e) {
                    log.error("[" + threadName + "] Lỗi khi lưu graph entities cho ID: " + eventId, e);
                }
            }, taskExecutor);

        } catch (Exception e) {
            log.error("Lỗi parse log từ " + source + ": " + e.getMessage());
        }
    }

    private BaseEvent resolveEvent(String source, String rawData) {
        try {
            return objectMapper.readValue(rawData, BaseEvent.class);
        } catch (Exception ignored) {
            return parserDispatcher.parse(source, rawData);
        }
    }
}