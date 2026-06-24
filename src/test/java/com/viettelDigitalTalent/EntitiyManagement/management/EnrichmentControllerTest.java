package com.viettelDigitalTalent.EntitiyManagement.management;

import com.viettelDigitalTalent.EntitiyManagement.management.controller.EnrichmentController;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrichmentControllerTest {

    @Mock private AuditLogRepository auditLogRepository;

    private EnrichmentController controller;

    @BeforeEach
    void setUp() {
        controller = new EnrichmentController(auditLogRepository);
    }

    @Test
    void getByEventId_returnsEnrichmentMap() {
        Map<String, Object> enrichment = Map.of("geo", Map.of("country", "US"));
        when(auditLogRepository.findEnrichmentByEventId("evt-1")).thenReturn(enrichment);

        ResponseEntity<Map<String, Object>> response = controller.getByEventId("evt-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("geo");
    }

    @Test
    void getByEventId_returnsEmptyMapWhenNotFound() {
        when(auditLogRepository.findEnrichmentByEventId("missing")).thenReturn(Collections.emptyMap());

        ResponseEntity<Map<String, Object>> response = controller.getByEventId("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getByEventId_delegatesToRepository() {
        when(auditLogRepository.findEnrichmentByEventId(anyString())).thenReturn(Map.of());

        controller.getByEventId("any-id");

        verify(auditLogRepository, times(1)).findEnrichmentByEventId("any-id");
    }
}
