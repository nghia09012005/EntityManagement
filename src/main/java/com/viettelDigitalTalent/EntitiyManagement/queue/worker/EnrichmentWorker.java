package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class EnrichmentWorker {

//     private final EnrichmentService enrichmentService;
//     private final AuditLogRepository auditLogRepository;
//     private final ObjectMapper objectMapper;
//     private final DeadLetterPublisher deadLetterPublisher;

//     private static final String[] ENRICHMENT_KEYS = {
//         "geo", "malware", "srcGeo", "dstGeo", "ipIntel", "srcIpIntel", "dstIpIntel"
//     };

//     @KafkaListener(topics = KafkaTopicConstants.NORMALIZED_EVENTS, groupId = "soc-enrichment-group")
//     public void consume(String payload) {
//         try {
//             BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
//             String eventId = event.getEventId();

//             log.info("[EnrichmentWorker] Enriching event ID: {}", eventId);
//             enrichmentService.enrich(event);

//             Map<String, Object> enrichmentData = new HashMap<>();
//             for (String key : ENRICHMENT_KEYS) {
//                 if (event.getRawData().containsKey(key)) {
//                     enrichmentData.put(key, event.getRawData().get(key));
//                 }
//             }
//             auditLogRepository.updateEnrichment(eventId, enrichmentData);
//             log.info("[EnrichmentWorker] Enrichment done cho ID: {}", eventId);

//             // test DLQ
// //            throw new RuntimeException("Intentional test exception");

//         } catch (Exception e) {
//             log.error("[EnrichmentWorker] Lỗi enrich event: {}", e.getMessage(), e);
//             deadLetterPublisher.publish(KafkaTopicConstants.NORMALIZED_EVENTS, payload, e);
//         }
//     }
// }






// Enrichment Async 
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentWorker {

    private final EnrichmentService enrichmentService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final DeadLetterPublisher deadLetterPublisher;
    
    // Inject custom executor để kiểm soát số lượng luồng
    @Qualifier("enrichmentExecutor")
    private final Executor executor;

    private static final String[] ENRICHMENT_KEYS = {
        "geo", "malware", "srcGeo", "dstGeo", "ipIntel", "srcIpIntel", "dstIpIntel"
    };

    @KafkaListener(topics = KafkaTopicConstants.NORMALIZED_EVENTS, groupId = "soc-enrichment-group",
            concurrency = "3")
    public void consume(String payload) {
        // Chỉ cần 1 khối try-catch để bắt lỗi parse JSON ban đầu
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
                    enrichmentService.enrich(event);

                    Map<String, Object> enrichmentData = Arrays.stream(ENRICHMENT_KEYS)
                            .filter(key -> event.getRawData().containsKey(key))
                            .collect(Collectors.toMap(k -> k, k -> event.getRawData().get(k)));

                    if (!enrichmentData.isEmpty()) {
                        auditLogRepository.updateEnrichment(event.getEventId(), enrichmentData);
                    }
                    log.info("[EnrichmentWorker] Async success ID: {}", event.getEventId());
                } catch (Exception e) {
                    log.error("[EnrichmentWorker] Lỗi xử lý logic enrichment: {}", e.getMessage());
                    deadLetterPublisher.publish(KafkaTopicConstants.NORMALIZED_EVENTS, payload, e);
                }
            }, executor);
        } catch (Exception e) {
            // Lỗi này bắt trường hợp không thể đẩy vào executor (ví dụ: task queue bị full)
            log.error("[EnrichmentWorker] Critical error, cannot execute async task: {}", e.getMessage());
            deadLetterPublisher.publish(KafkaTopicConstants.NORMALIZED_EVENTS, payload, e);
        }
    }
}