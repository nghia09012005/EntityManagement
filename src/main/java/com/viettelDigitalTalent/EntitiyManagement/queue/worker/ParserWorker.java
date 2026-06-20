package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.type.TypeReference;
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

    @Autowired
    private MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter() {
        return Counter.builder("soc.events.processed")
                .description("Total events processed by ParserWorker")
                .register(meterRegistry);
    }

    // Lắng nghe topic 'raw-logs'
    @KafkaListener(topics = "raw-logs", groupId = "soc-parser-group")
    public void consume(ConsumerRecord<String, String> record) {
        String source = record.key();
        String rawJson = record.value();

        log.info("DEBUG Kafka Raw: {}", rawJson);

        try {
            BaseEvent event = resolveEvent(rawJson);

            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            log.info("Parsed event: " + event.getClass().getSimpleName() + " [ID: " + event.getEventId() + "]");
            eventsProcessedCounter().increment();

            String eventId = event.getEventId();
            String eventSource = source != null ? source : event.getSource();
            String eventCategory = event.getCategory();
            java.time.LocalDateTime timestamp = event.getTimestamp() != null ? event.getTimestamp() : java.time.LocalDateTime.now();

            // Lấy rawData từ JSON gốc (Kafka message), không từ event.getRawData()
            // vì parsers chỉ set typed fields và không populate rawData map
            Map<String, Object> rawDataSnapshot;
            try {
                rawDataSnapshot = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                rawDataSnapshot = new HashMap<>();
                rawDataSnapshot.put("_raw", rawJson);
            }

            // Bước 1 (sync): Lưu raw log ngay trên Kafka consumer thread — đảm bảo document
            // tồn tại trước khi bất kỳ async task nào chạy updateEnrichment (tránh race condition).
            try {
                log.info("[DEBUG] Trước khi lưu: ID={}, Category={}, RawDataSize={}",
                        eventId, eventCategory, (event.getRawData() != null ? event.getRawData().size() : "NULL"));
                auditLogRepository.saveRawLog(eventId, eventSource, eventCategory, timestamp, rawDataSnapshot);
                log.info("[consumer] Lưu raw log thành công cho ID: {}", eventId);
            } catch (Exception e) {
                log.error("[consumer] Lỗi khi lưu raw log cho ID: {}", eventId, e);
            }

            // Bước 2 (async): Enrich → cập nhật MongoDB enrichment
            // Bước 3 (async, sau bước 2): Lưu entity graph vào Neo4j
            CompletableFuture.runAsync(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    log.info("[{}] Bắt đầu enrich dữ liệu cho ID: {}", threadName, eventId);
                    enrichService.enrich(event);

                    Map<String, Object> enrichmentData = new HashMap<>();
                    if (event.getRawData().containsKey("geo"))     enrichmentData.put("geo",     event.getRawData().get("geo"));
                    if (event.getRawData().containsKey("malware")) enrichmentData.put("malware", event.getRawData().get("malware"));
                    if (event.getRawData().containsKey("srcGeo"))  enrichmentData.put("srcGeo",  event.getRawData().get("srcGeo"));
                    if (event.getRawData().containsKey("dstGeo"))  enrichmentData.put("dstGeo",  event.getRawData().get("dstGeo"));

                    auditLogRepository.updateEnrichment(eventId, enrichmentData);
                    log.info("[{}] Cập nhật enrich thành công cho ID: {}", threadName, eventId);
                } catch (Exception e) {
                    log.error("[{}] Lỗi khi enrich/cập nhật cho ID: {}", threadName, eventId, e);
                }
            }, taskExecutor).thenRunAsync(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    log.info("[{}] Bắt đầu lưu graph entities cho ID: {}", threadName, eventId);
                    graphEntityService.save(event);
                    log.info("[{}] Lưu graph entities thành công cho ID: {}", threadName, eventId);
                } catch (Exception e) {
                    log.error("[{}] Lỗi khi lưu graph entities cho ID: {}", threadName, eventId, e);
                }
            }, taskExecutor);

        } catch (Exception e) {
            log.error("Lỗi parse log từ {}: {}", source, e.getMessage());
        }
    }

    private BaseEvent resolveEvent(String rawJson) {
        try {
            BaseEvent event = objectMapper.readValue(rawJson, BaseEvent.class);

            // Gán Category dựa trên Class thực tế của đối tượng
            if (event instanceof com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent) {
                event.setCategory(EventCategory.AUTHENTICATION.name());
            } else if (event instanceof com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent) {
                event.setCategory(EventCategory.PROCESS.name());
            } else if (event instanceof com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent) {
                event.setCategory(EventCategory.NETWORK.name());
            } else if (event instanceof com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent) {
                event.setCategory(EventCategory.THREAT.name());
            }

            return event;
        } catch (Exception ignored) {
            log.warn("Auto detect");
            return parserDispatcher.autoDetect(rawJson);
        }
    }
}