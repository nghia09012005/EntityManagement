package com.viettelDigitalTalent.EntitiyManagement.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import com.viettelDigitalTalent.EntitiyManagement.queue.worker.ParserWorker;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParserWorkerTest {

    private ParserWorker worker;

    @Mock ParserDispatcher parserDispatcher;
    @Mock AuditLogRepository auditLogRepository;
    @Mock LlmProcess llmProcess;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Mock DeadLetterPublisher deadLetterPublisher;

    @BeforeEach
    void setUp() {
        worker = new ParserWorker();
        ReflectionTestUtils.setField(worker, "parserDispatcher",   parserDispatcher);
        ReflectionTestUtils.setField(worker, "objectMapper",       new ObjectMapper());
        ReflectionTestUtils.setField(worker, "taskExecutor",       new SyncTaskExecutor());
        ReflectionTestUtils.setField(worker, "auditLogRepository", auditLogRepository);
        ReflectionTestUtils.setField(worker, "llmProcess",         llmProcess);
        ReflectionTestUtils.setField(worker, "meterRegistry",      new SimpleMeterRegistry());
        ReflectionTestUtils.setField(worker, "kafkaTemplate",      kafkaTemplate);
        ReflectionTestUtils.setField(worker, "deadLetterPublisher",deadLetterPublisher);

        // Flat-field JSON now routes through autoDetect — provide a lenient default so
        // individual tests that don't care about event content still pass end-to-end.
        AlertEvent defaultAlert = new AlertEvent();
        defaultAlert.setAlertName("default");
        defaultAlert.setSeverity("LOW");
        lenient().when(parserDispatcher.autoDetect(anyString())).thenReturn(defaultAlert);
    }

    // ── Valid JSON → audit log + publish normalized ───────────────────────────

    @Test
    void consumeAuthJson_savesAuditLogAndPublishesNormalized() {
        String json = "{\"eventType\":\"AUTHENTICATION\",\"username\":\"admin\",\"workstation\":\"WIN-PC01\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, "windows", json);

        worker.consume(record);

        verify(auditLogRepository).saveRawLog(any(), any(), any(), any(), any(), any());
        verify(kafkaTemplate).send(anyString(), anyString());
    }

    @Test
    void consumeAlertJson_savesAuditLogAndPublishesNormalized() {
        String json = "{\"eventType\":\"ALERT\",\"alertName\":\"Brute Force\",\"severity\":\"HIGH\",\"targetIp\":\"1.2.3.4\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, "alert", json);

        worker.consume(record);

        verify(auditLogRepository).saveRawLog(any(), any(), any(), any(), any(), any());
        verify(kafkaTemplate).send(anyString(), anyString());
    }

    @Test
    void consumeNetworkJson_savesAuditLog() {
        String json = "{\"eventType\":\"NETWORK\",\"srcIp\":\"10.0.0.1\",\"dstIp\":\"1.2.3.4\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, null, json);

        worker.consume(record);

        verify(auditLogRepository).saveRawLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    void consumeProcessJson_savesAuditLog() {
        String json = "{\"eventType\":\"PROCESS\",\"processName\":\"cmd.exe\",\"fileHash\":\"abc123\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, "process", json);

        worker.consume(record);

        verify(auditLogRepository).saveRawLog(any(), any(), any(), any(), any(), any());
    }

    // ── Free-text path (LLM async) ────────────────────────────────────────────

    @Test
    void consumeFreeText_callsLlmProcessAndPublishes() {
        String freeText = "Failed password for admin from 185.220.101.1 port 22 ssh2";

        AlertEvent placeholder = new AlertEvent();
        placeholder.setAlertName("Pending LLM Analysis");
        placeholder.setSeverity("LOW");
        placeholder.setSource("free-text");
        when(parserDispatcher.autoDetect(freeText)).thenReturn(placeholder);

        AlertEvent llmResult = new AlertEvent();
        llmResult.setAlertName("SSH Brute Force");
        llmResult.setSeverity("HIGH");
        when(llmProcess.extractAlert(freeText)).thenReturn(llmResult);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, null, freeText);
        worker.consume(record);

        verify(llmProcess).extractAlert(freeText);
        verify(kafkaTemplate).send(anyString(), anyString());
    }

    @Test
    void consumeFreeText_handlesNullLlmResult() {
        String freeText = "some plain text log line without JSON";

        AlertEvent placeholder = new AlertEvent();
        placeholder.setAlertName("Pending LLM Analysis");
        placeholder.setSeverity("LOW");
        when(parserDispatcher.autoDetect(freeText)).thenReturn(placeholder);
        when(llmProcess.extractAlert(freeText)).thenReturn(null);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, null, freeText);
        worker.consume(record);

        verify(kafkaTemplate).send(anyString(), anyString());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void consumeDoesNotThrowOnAuditSaveError() {
        String json = "{\"eventType\":\"AUTHENTICATION\",\"username\":\"admin\",\"workstation\":\"WIN-PC01\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, "windows", json);
        doThrow(new RuntimeException("mongo down")).when(auditLogRepository).saveRawLog(any(), any(), any(), any(), any(), any());

        worker.consume(record); // must not propagate exception
    }

    @Test
    void consumeNullKeyUsesEventSource() {
        String json = "{\"eventType\":\"ALERT\",\"alertName\":\"Test\",\"severity\":\"LOW\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, null, json);

        worker.consume(record);

        verify(auditLogRepository).saveRawLog(any(), isNull(), any(), any(), any(), any());
    }

    @Test
    void consumeInvalidJson_fallsBackToAutoDetect() {
        String badJson = "not valid json {";
        AlertEvent event = new AlertEvent();
        event.setAlertName("Parsed");
        event.setSeverity("LOW");
        when(parserDispatcher.autoDetect(badJson)).thenReturn(event);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, null, badJson);
        worker.consume(record);

        verify(parserDispatcher).autoDetect(badJson);
    }

    @Test
    void consumePublishError_sendsToDeadLetter() {
        String json = "{\"eventType\":\"AUTHENTICATION\",\"username\":\"admin\",\"workstation\":\"WIN-PC01\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("raw-logs", 0, 0L, "windows", json);
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        worker.consume(record);

        verify(deadLetterPublisher).publish(anyString(), anyString(), any(Exception.class));
    }
}
