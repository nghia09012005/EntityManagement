package com.viettelDigitalTalent.EntitiyManagement.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorrelationWorkerTest {

    @Test
    void onEvent_extractsTenantAndEvaluatesRecentEvents() {
        CorrelationService service = mock(CorrelationService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.findRecentEvents(any(LocalDateTime.class))).thenReturn(List.of(new AuditLog()));
        CorrelationWorker worker = new CorrelationWorker(service, auditLogRepository, new ObjectMapper());

        worker.onEvent("{\"tenantId\":\"tenant-7\"}");

        verify(service).evaluate(anyList(), any(LocalDateTime.class), eq("tenant-7"));
    }

    @Test
    void onEvent_usesDefaultTenantWhenPayloadInvalid() {
        CorrelationService service = mock(CorrelationService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.findRecentEvents(any(LocalDateTime.class))).thenReturn(List.of());
        CorrelationWorker worker = new CorrelationWorker(service, auditLogRepository, new ObjectMapper());

        worker.onEvent("not json");

        verify(service).evaluate(anyList(), any(LocalDateTime.class), eq("default"));
    }

    @Test
    void onEvent_debouncesBurst() {
        CorrelationService service = mock(CorrelationService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.findRecentEvents(any(LocalDateTime.class))).thenReturn(List.of());
        CorrelationWorker worker = new CorrelationWorker(service, auditLogRepository, new ObjectMapper());

        worker.onEvent("{\"tenantId\":\"tenant-1\"}");
        worker.onEvent("{\"tenantId\":\"tenant-1\"}");

        verify(service, times(1)).evaluate(anyList(), any(LocalDateTime.class), eq("tenant-1"));
    }
}
