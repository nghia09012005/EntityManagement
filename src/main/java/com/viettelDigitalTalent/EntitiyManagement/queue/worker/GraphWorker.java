package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphEntityService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphWorker {

    private final GraphEntityService graphEntityService;
    private final ObjectMapper objectMapper;
    private final DeadLetterPublisher deadLetterPublisher;

    @KafkaListener(topics = KafkaTopicConstants.NORMALIZED_EVENTS, groupId = "soc-graph-group",
            concurrency = "3")
    public void consume(String payload) {
        try {
            BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
            log.info("[GraphWorker] Saving graph entities for event ID: {}", event.getEventId());
            graphEntityService.save(event);
            log.info("[GraphWorker] Graph entities saved for event ID: {}", event.getEventId());
        } catch (Exception e) {
            log.error("[GraphWorker] Lỗi lưu graph cho event: {}", e.getMessage(), e);
            deadLetterPublisher.publish(KafkaTopicConstants.NORMALIZED_EVENTS, payload, e);
        }
    }
}
