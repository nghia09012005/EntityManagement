package com.viettelDigitalTalent.EntitiyManagement.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants;
import com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicsConfig;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import com.viettelDigitalTalent.EntitiyManagement.queue.worker.EnrichmentWorker;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueCoverageTest {

    @Test
    void kafkaTopicsConfig_buildsExpectedTopics() {
        KafkaTopicsConfig config = new KafkaTopicsConfig();

        assertThat(config.normalizedEventsTopic().name()).isEqualTo(KafkaTopicConstants.NORMALIZED_EVENTS);
        assertThat(config.enrichedEventsTopic().name()).isEqualTo(KafkaTopicConstants.ENRICHED_EVENTS);
        assertThat(config.deadLetterQueueTopic().name()).isEqualTo(KafkaTopicConstants.DEAD_LETTER_QUEUE);
    }

    @Test
    void deadLetterPublisher_serializesPayloadWithTenant() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterPublisher publisher = new DeadLetterPublisher(kafkaTemplate, new ObjectMapper());

        publisher.publish("raw-logs", null, "tenant-1", new RuntimeException("boom"));

        verify(kafkaTemplate).send(eq(KafkaTopicConstants.DEAD_LETTER_QUEUE), contains("\"tenantId\":\"tenant-1\""));
    }

    @Test
    void deadLetterPublisher_handlesSerializationFailure() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad json") {});

        new DeadLetterPublisher(kafkaTemplate, mapper)
                .publish("raw-logs", "payload", new RuntimeException("boom"));

        verifyNoInteractions(kafkaTemplate);
    }

    // @Test
    // void enrichmentWorker_updatesOnlyEnrichmentKeys() {
    //     EnrichmentService enrichmentService = mock(EnrichmentService.class);
    //     AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    //     DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    //     Executor direct = Runnable::run;
    //     EnrichmentWorker worker = new EnrichmentWorker(
    //             enrichmentService, auditLogRepository, new ObjectMapper(), deadLetterPublisher, direct);

    //     doAnswer(invocation -> {
    //         BaseEvent event = invocation.getArgument(0);
    //         event.getRawData().put("malware", Map.of("verdict", "MALICIOUS"));
    //         return null;
    //     }).when(enrichmentService).enrich(any(BaseEvent.class));

    //     String payload = """
    //             {"eventType":"ALERT","uid":"event-1","raw_data":{"geo":{"country":"VN"},"noise":true}}
    //             """;
    //     worker.consume(payload);

    //     verify(auditLogRepository).updateEnrichment(eq("event-1"), argThat(map ->
    //             map.containsKey("geo") && map.containsKey("malware") && !map.containsKey("noise")));
    //     verifyNoInteractions(deadLetterPublisher);
    // }

    @Test
    void enrichmentWorker_sendsDlqWhenLogicFails() {
        EnrichmentService enrichmentService = mock(EnrichmentService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
        EnrichmentWorker worker = new EnrichmentWorker(
                enrichmentService, auditLogRepository, new ObjectMapper(), deadLetterPublisher, Runnable::run);
        doThrow(new RuntimeException("enrich down")).when(enrichmentService).enrich(any(BaseEvent.class));

        String payload = "{\"eventType\":\"ALERT\",\"uid\":\"event-1\"}";
        worker.consume(payload);

        verify(deadLetterPublisher).publish(eq(KafkaTopicConstants.NORMALIZED_EVENTS), eq(payload), any(Exception.class));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void enrichmentWorker_sendsDlqWhenExecutorRejects() {
        EnrichmentWorker worker = new EnrichmentWorker(
                mock(EnrichmentService.class),
                mock(AuditLogRepository.class),
                new ObjectMapper(),
                mock(DeadLetterPublisher.class),
                command -> { throw new RejectedExecutionException("queue full"); });
        DeadLetterPublisher dlq = org.springframework.test.util.ReflectionTestUtils.getField(worker, "deadLetterPublisher") instanceof DeadLetterPublisher p ? p : null;

        String payload = "{\"eventType\":\"ALERT\",\"uid\":\"event-1\"}";
        worker.consume(payload);

        verify(dlq).publish(eq(KafkaTopicConstants.NORMALIZED_EVENTS), eq(payload), any(Exception.class));
    }
}
