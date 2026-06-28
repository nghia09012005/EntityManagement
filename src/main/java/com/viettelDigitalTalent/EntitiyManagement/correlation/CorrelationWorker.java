package com.viettelDigitalTalent.EntitiyManagement.correlation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelationWorker {

    private final CorrelationService correlationService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private final AtomicLong lastEvaluatedAt = new AtomicLong(0);
    private static final long DEBOUNCE_MS = 10_000;
    private static final int WINDOW_MINUTES = 30;
    private static final String DEFAULT_TENANT = "default";

    @KafkaListener(topics = KafkaTopicConstants.NORMALIZED_EVENTS, groupId = "soc-correlation-group")
    public void onEvent(String payload) {
        long now = System.currentTimeMillis();
        if (now - lastEvaluatedAt.get() < DEBOUNCE_MS) return;
        lastEvaluatedAt.set(now);

        String tenantId = extractTenantId(payload);

        // windowStart snapped to current hour for dedup
        LocalDateTime windowStart = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime since = windowStart.minusMinutes(WINDOW_MINUTES);

        List<AuditLog> events = auditLogRepository.findRecentEvents(since);
        log.info("[CorrelationWorker] Evaluating {} events in window {} for tenant {}", events.size(), windowStart, tenantId);
        correlationService.evaluate(events, windowStart, tenantId);
    }

    private String extractTenantId(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode tenantNode = node.get("tenantId");
            if (tenantNode != null && !tenantNode.isNull() && !tenantNode.asText().isBlank()) {
                return tenantNode.asText();
            }
        } catch (Exception e) {
            log.warn("[CorrelationWorker] Không parse được tenantId từ payload: {}", e.getMessage());
        }
        return DEFAULT_TENANT;
    }
}