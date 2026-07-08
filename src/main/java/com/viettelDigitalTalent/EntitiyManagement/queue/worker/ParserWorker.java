package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.dto.IngestionEnvelope;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.UnknownFieldDetector;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UnknownFieldStatRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UnknownFieldOccurrenceRepository;
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
    @Autowired private UnknownFieldDetector unknownFieldDetector;
    @Autowired private UnknownFieldStatRepository unknownFieldStatRepository;
    @Autowired private UnknownFieldOccurrenceRepository unknownFieldOccurrenceRepository;

    private Counter eventsProcessedCounter() {
        return Counter.builder("soc.events.processed")
                .description("Total events processed by ParserWorker")
                .register(meterRegistry);
    }

    @KafkaListener(topics = KafkaTopicConstants.RAW_LOGS, groupId = "soc-parser-group",
            concurrency = "3")
    public void consume(ConsumerRecord<String, String> record) {
        String source  = record.key();
        String rawJson = record.value();

        // Unwrap envelope để lấy tenantId từ Ingestion API
        String tenantId = null;
        String actualPayload = rawJson;
        try {
            IngestionEnvelope envelope = objectMapper.readValue(rawJson, IngestionEnvelope.class);
            if (envelope.tenantId() != null && envelope.payload() != null) {
                tenantId    = envelope.tenantId();
                actualPayload = envelope.payload();
                log.debug("[ParserWorker] Unwrapped envelope tenantId={}", tenantId);
            }
        } catch (Exception ignored) {
            // raw log không phải envelope — xử lý bình thường
        }

        final String finalTenantId = tenantId;
        final String rawPayload    = actualPayload;

        try {
            BaseEvent event = resolveEvent(rawPayload);

            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            event.setTenantId(finalTenantId);
            hydrateRawData(event);
            captureUnknownFields(rawPayload, event);

            log.info("Parsed event: {} [ID: {}] tenantId={}", event.getClass().getSimpleName(), event.getEventId(), finalTenantId);
            eventsProcessedCounter().increment();

            String eventId       = event.getEventId();
            String eventSource   = source != null ? source : event.getSource();
            String eventCategory = event.getCategory();
            LocalDateTime timestamp = event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now();

            Map<String, Object> rawDataSnapshot = new HashMap<>(event.getRawData());

            // Sync: lưu raw log ngay trên Kafka consumer thread — đảm bảo document
            // tồn tại trước khi EnrichmentWorker chạy updateEnrichment.
            try {
                auditLogRepository.saveRawLog(eventId, finalTenantId, eventSource, eventCategory, timestamp, rawDataSnapshot, rawPayload);
                log.info("[ParserWorker] Lưu raw log thành công cho ID: {}", eventId);
            } catch (Exception e) {
                log.error("[ParserWorker] Lỗi khi lưu raw log cho ID: {}", eventId, e);
            }

            boolean isLlmFallback = event instanceof AlertEvent alert
                    && ("free-text".equals(alert.getSource()) || "llm-fallback".equals(alert.getSource()));

            if (isLlmFallback && event instanceof AlertEvent alertEvent) {
                // Free-text/unknown JSON: chạy LLM async rồi mới publish normalized-events.
                // Nếu LLM không hiểu được, exception sẽ đẩy payload xuống DLQ.
                CompletableFuture.runAsync(() -> {
                    try {
                        AlertEvent llmResult = llmProcess.extractAlert(rawPayload);
                        if (llmResult != null) {
                            alertEvent.setActivityId(llmResult.getActivityId());
                            alertEvent.setSeverityId(llmResult.getSeverityId());
                            alertEvent.setTime(llmResult.getTime());
                            alertEvent.setMessage(llmResult.getMessage());
                            if (llmResult.getAlertName()   != null) alertEvent.setAlertName(llmResult.getAlertName());
                            if (llmResult.getSeverity()    != null) alertEvent.setSeverity(llmResult.getSeverity());
                            if (llmResult.getDescription() != null) alertEvent.setDescription(llmResult.getDescription());
                            alertEvent.setTargetIp(llmResult.getTargetIp());
                            alertEvent.setTargetUser(llmResult.getTargetUser());
                            alertEvent.setTargetHost(llmResult.getTargetHost());
                            alertEvent.setTargetDomain(llmResult.getTargetDomain());
                            alertEvent.setTargetFileHash(llmResult.getTargetFileHash());
                            alertEvent.setTargetUrl(llmResult.getTargetUrl());
                            alertEvent.setTargetCloudResourceId(llmResult.getTargetCloudResourceId());
                            alertEvent.setTargetEmail(llmResult.getTargetEmail());
                            alertEvent.setTargetCve(llmResult.getTargetCve());
                            if (llmResult.getSourceIp() != null) alertEvent.setSourceIp(llmResult.getSourceIp());
                            if (llmResult.getSourceHost() != null) alertEvent.setSourceHost(llmResult.getSourceHost());
                            if (llmResult.getSourceDomain() != null) alertEvent.setSourceDomain(llmResult.getSourceDomain());
                            hydrateRawData(alertEvent);
                        }
                        publishNormalized(alertEvent, rawPayload, finalTenantId);
                    } catch (Exception e) {
                        log.error("[ParserWorker] Lỗi LLM cho ID: {}", eventId, e);
                        deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, rawPayload, finalTenantId, e);
                    }
                }, taskExecutor);
            } else {
                publishNormalized(event, rawPayload, finalTenantId);
            }

        } catch (Exception e) {
            log.error("Lỗi parse log từ {}: {}", source, e.getMessage());
            deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, rawPayload, finalTenantId, e);
        }
    }

    private void publishNormalized(BaseEvent event, String originalRaw, String tenantId) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopicConstants.NORMALIZED_EVENTS, payload);
            log.info("[ParserWorker] Published to normalized-events, ID: {}", event.getEventId());
        } catch (Exception e) {
            log.error("[ParserWorker] Lỗi publish normalized event ID: {}", event.getEventId(), e);
            deadLetterPublisher.publish(KafkaTopicConstants.RAW_LOGS, originalRaw, tenantId, e);
        }
    }

    private BaseEvent resolveEvent(String rawJson) {
        try {
            BaseEvent event = objectMapper.readValue(rawJson, BaseEvent.class);

            // Jackson created the right subtype but flat-field JSON left it empty
            // (bridge setters have no @JsonProperty) — re-route through the parser
            if (isMissingPrimaryFields(event)) {
                log.debug("[ParserWorker] eventType detected but payload uses flat fields — routing to parser");
                return parserDispatcher.autoDetect(rawJson);
            }

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

    private void hydrateRawData(BaseEvent event) {
        if (event instanceof AuthenticationEvent auth) {
            if (auth.getUsername() != null) auth.getRawData().put("username", auth.getUsername());
            if (auth.getIpAddress() != null) auth.getRawData().put("ipAddress", auth.getIpAddress());
            if (auth.getWorkstation() != null) auth.getRawData().put("workstation", auth.getWorkstation());
            auth.getRawData().put("success", auth.isSuccess());
            return;
        }

        if (event instanceof ProcessEvent proc) {
            if (proc.getProcessName() != null) proc.getRawData().put("processName", proc.getProcessName());
            if (proc.getProcessPath() != null) proc.getRawData().put("processPath", proc.getProcessPath());
            if (proc.getFileHash() != null) proc.getRawData().put("fileHash", proc.getFileHash());
            if (proc.getCommandLine() != null) proc.getRawData().put("commandLine", proc.getCommandLine());
            if (proc.getRawData().get("hostname") == null && proc.getRawData().get("raw_data") instanceof Map<?, ?> rawData) {
                Object hostname = rawData.get("hostname");
                if (hostname != null) proc.getRawData().put("hostname", hostname);
            }
            return;
        }

        if (event instanceof NetworkEvent net) {
            if (net.getSrcIp() != null) net.getRawData().put("srcIp", net.getSrcIp());
            if (net.getDstIp() != null) net.getRawData().put("dstIp", net.getDstIp());
            if (net.getDstDomain() != null) net.getRawData().put("dstDomain", net.getDstDomain());
            if (net.getDstPort() > 0) net.getRawData().put("dstPort", net.getDstPort());
            return;
        }

        if (event instanceof AlertEvent alert) {
            if (alert.getAlertName() != null) alert.getRawData().put("alertName", alert.getAlertName());
            if (alert.getSeverity() != null) alert.getRawData().put("severity", alert.getSeverity());
            if (alert.getDescription() != null) alert.getRawData().put("description", alert.getDescription());
            if (alert.getTargetIp() != null) alert.getRawData().put("targetIp", alert.getTargetIp());
            if (alert.getTargetUser() != null) alert.getRawData().put("targetUser", alert.getTargetUser());
            if (alert.getTargetHost() != null) alert.getRawData().put("targetHost", alert.getTargetHost());
            if (alert.getTargetDomain() != null) alert.getRawData().put("targetDomain", alert.getTargetDomain());
            if (alert.getTargetFileHash() != null) alert.getRawData().put("targetFileHash", alert.getTargetFileHash());
            if (alert.getTargetProcess() != null) alert.getRawData().put("targetProcess", alert.getTargetProcess());
            if (alert.getTargetUrl() != null) alert.getRawData().put("targetUrl", alert.getTargetUrl());
            if (alert.getTargetCloudResourceId() != null) alert.getRawData().put("targetCloudResourceId", alert.getTargetCloudResourceId());
            if (alert.getTargetEmail() != null) alert.getRawData().put("targetEmail", alert.getTargetEmail());
            if (alert.getTargetCve() != null) alert.getRawData().put("targetCve", alert.getTargetCve());
        }
    }

    private void captureUnknownFields(String rawPayload, BaseEvent event) {
        if (rawPayload == null || !rawPayload.trim().startsWith("{")) return;
        if (event instanceof AlertEvent alert
                && ("custom-parser".equals(alert.getSource()) || "llm-fallback".equals(alert.getSource()))) {
            return;
        }

        Map<String, Object> unknown = unknownFieldDetector.detect(rawPayload, event.getCategory(), event.getTenantId());
        Map<String, Object> customFieldValues = unknownFieldDetector.collectCustomFieldValues(rawPayload, event.getCategory(), event.getTenantId());
        if (!customFieldValues.isEmpty()) {
            customFieldValues.forEach((fieldName, value) -> event.getRawData().putIfAbsent(fieldName, value));
        }
        if (unknown.isEmpty()) return;

        event.setUnknownFields(new HashMap<>(unknown));
        unknownFieldStatRepository.record(event.getCategory(), event.getEventId(), unknown);
        unknown.forEach((fieldName, value) -> unknownFieldOccurrenceRepository.record(
            event.getTenantId(),
            event.getCategory(),
            event.getEventId(),
            fieldName,
            value != null ? String.valueOf(value) : null,
            event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now()
        ));
        log.info("[ParserWorker] Phát hiện {} trường lạ cho {}: {}",
                unknown.size(), event.getCategory(), unknown.keySet());
    }

    /** True khi Jackson tạo được event nhưng không populate field nào có ý nghĩa. */
    private boolean isMissingPrimaryFields(BaseEvent event) {
        if (event instanceof AlertEvent ae)           return ae.getAlertName() == null && ae.getTargetIp() == null;
        if (event instanceof AuthenticationEvent ae)  return ae.getUsername()   == null && ae.getIpAddress() == null;
        if (event instanceof NetworkEvent ne)         return ne.getSrcIp()      == null && ne.getDstIp()     == null;
        if (event instanceof ProcessEvent pe)         return pe.getProcessName() == null && pe.getFileHash()  == null;
        return false;
    }
}
