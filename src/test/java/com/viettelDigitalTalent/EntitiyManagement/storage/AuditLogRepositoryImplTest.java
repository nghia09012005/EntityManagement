package com.viettelDigitalTalent.EntitiyManagement.storage;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogRepositoryImplTest {

    private AuditLogRepositoryImpl repo;

    @Mock MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        repo = new AuditLogRepositoryImpl();
        ReflectionTestUtils.setField(repo, "mongoTemplate", mongoTemplate);
    }

    @Test
    void saveRawLogCallsUpsert() {
        repo.saveRawLog("event-1", "kafka", "AUTHENTICATION", LocalDateTime.now(), Map.of("user", "admin"), null);
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(AuditLog.class));
    }

    @Test
    void saveRawLogWithNullSourceDoesNotThrow() {
        repo.saveRawLog("event-2", null, "PROCESS", LocalDateTime.now(), Map.of(), null);
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(AuditLog.class));
    }

    @Test
    void saveRawLogWithEmptyRawDataDoesNotThrow() {
        repo.saveRawLog("event-3", "file", "ALERT", LocalDateTime.now(), Map.of(), null);
        verify(mongoTemplate).upsert(any(), any(), eq(AuditLog.class));
    }

    @Test
    void saveRawLogDoesNotCallUpdateFirst() {
        repo.saveRawLog("event-4", "kafka", "NETWORK", LocalDateTime.now(), Map.of(), null);
        verify(mongoTemplate, never()).updateFirst(any(), any(), eq(AuditLog.class));
    }

    @Test
    void updateEnrichmentCallsUpdateFirst() {
        repo.updateEnrichment("event-1", Map.of("geo", Map.of("country", "Vietnam")));
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(AuditLog.class));
    }

    @Test
    void updateEnrichmentDoesNotCallUpsert() {
        repo.updateEnrichment("event-1", Map.of());
        verify(mongoTemplate, never()).upsert(any(), any(), eq(AuditLog.class));
    }

    @Test
    void updateEnrichmentWithEmptyMapDoesNotThrow() {
        repo.updateEnrichment("event-5", Map.of());
        verify(mongoTemplate).updateFirst(any(), any(), eq(AuditLog.class));
    }

    @Test
    void saveRawLogAndUpdateEnrichmentUseAuditLogClass() {
        repo.saveRawLog("e1", "s", "c", LocalDateTime.now(), Map.of(), null);
        repo.updateEnrichment("e1", Map.of());

        verify(mongoTemplate, times(1)).upsert(any(), any(), eq(AuditLog.class));
        verify(mongoTemplate, times(1)).updateFirst(any(), any(), eq(AuditLog.class));
    }

    @Test
    void saveRawLogWithNullTimestampDoesNotThrow() {
        repo.saveRawLog("event-6", "kafka", "AUTHENTICATION", null, Map.of(), null);
        verify(mongoTemplate).upsert(any(), any(), eq(AuditLog.class));
    }

    @Test
    void multipleEnrichmentUpdatesAreIndependent() {
        repo.updateEnrichment("event-A", Map.of("key1", "val1"));
        repo.updateEnrichment("event-B", Map.of("key2", "val2"));
        verify(mongoTemplate, times(2)).updateFirst(any(), any(), eq(AuditLog.class));
    }

    // ── findEnrichmentByEventId ───────────────────────────────────────────────

    @Test
    void findEnrichmentByEventId_returnsEmptyMapWhenNull() {
        Map<String, Object> result = repo.findEnrichmentByEventId(null);
        assertThat(result).isEmpty();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void findEnrichmentByEventId_returnsEmptyMapWhenBlank() {
        Map<String, Object> result = repo.findEnrichmentByEventId("   ");
        assertThat(result).isEmpty();
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void findEnrichmentByEventId_returnsEnrichmentWhenFound() {
        AuditLog log = new AuditLog();
        log.setEventId("evt-1");
        Map<String, Object> enrichment = Map.of("geo", Map.of("country", "US"));
        log.setEnrichment(enrichment);
        when(mongoTemplate.findById("evt-1", AuditLog.class)).thenReturn(log);

        Map<String, Object> result = repo.findEnrichmentByEventId("evt-1");

        assertThat(result).containsKey("geo");
    }

    @Test
    void findEnrichmentByEventId_returnsEmptyMapWhenNotFound() {
        when(mongoTemplate.findById("missing", AuditLog.class)).thenReturn(null);

        Map<String, Object> result = repo.findEnrichmentByEventId("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void findEnrichmentByEventId_returnsEmptyMapWhenEnrichmentFieldNull() {
        AuditLog log = new AuditLog();
        log.setEventId("evt-2");
        log.setEnrichment(null);
        when(mongoTemplate.findById("evt-2", AuditLog.class)).thenReturn(log);

        Map<String, Object> result = repo.findEnrichmentByEventId("evt-2");

        assertThat(result).isEmpty();
    }
}
