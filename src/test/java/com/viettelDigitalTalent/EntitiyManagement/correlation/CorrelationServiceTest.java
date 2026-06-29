package com.viettelDigitalTalent.EntitiyManagement.correlation;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationServiceTest {

    @Mock
    private CorrelationRule rule;

    @Mock
    private IncidentRepository incidentRepository;

    private CorrelationService service;

    private final LocalDateTime WINDOW = LocalDateTime.of(2024, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        service = new CorrelationService(List.of(rule), incidentRepository);
    }

    private AuditLog log(String eventId, Map<String, Object> rawData) {
        AuditLog l = new AuditLog();
        l.setEventId(eventId);
        l.setRawData(rawData);
        return l;
    }

    @Test
    void evaluate_skipsWhenEventsEmpty() {
        service.evaluate(List.of(), WINDOW, "tenant-x");

        verify(rule, never()).evaluate(any(), any());
    }

    @Test
    void evaluate_savesIncidentWhenRuleMatchesAndNotExists() {
        AuditLog event = log("e1", Map.of("key", "val"));
        Incident incident = new Incident();
        incident.setPatternName("BruteForce");
        incident.setWindowStart(WINDOW);

        when(rule.evaluate(any(), any())).thenReturn(Optional.of(incident));
        when(incidentRepository.existsByPatternNameAndWindowStartAndTenantId(
                anyString(), any(LocalDateTime.class), anyString())).thenReturn(false);

        service.evaluate(List.of(event), WINDOW, "tenant-x");

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void evaluate_skipsDuplicateIncident() {
        AuditLog event = log("e1", Map.of("key", "val"));
        Incident incident = new Incident();
        incident.setPatternName("BruteForce");
        incident.setWindowStart(WINDOW);

        when(rule.evaluate(any(), any())).thenReturn(Optional.of(incident));
        when(incidentRepository.existsByPatternNameAndWindowStartAndTenantId(
                anyString(), any(LocalDateTime.class), anyString())).thenReturn(true);

        service.evaluate(List.of(event), WINDOW, "tenant-x");

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void evaluate_setsIdAndTenantIdBeforeSave() {
        AuditLog event = log("e1", Map.of("key", "val"));
        Incident incident = new Incident();
        incident.setPatternName("BruteForce");

        when(rule.evaluate(any(), any())).thenReturn(Optional.of(incident));
        when(incidentRepository.existsByPatternNameAndWindowStartAndTenantId(
                anyString(), any(LocalDateTime.class), anyString())).thenReturn(false);

        service.evaluate(List.of(event), WINDOW, "tenant-x");

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved.getId()).isNotNull().isNotBlank();
        assertThat(saved.getTenantId()).isEqualTo("tenant-x");
    }

    @Test
    void evaluate_continuesWhenRuleThrows() {
        AuditLog event = log("e1", Map.of("key", "val"));

        when(rule.evaluate(any(), any())).thenThrow(new RuntimeException("rule error"));

        // should not propagate
        service.evaluate(List.of(event), WINDOW, "tenant-x");

        verify(incidentRepository, never()).save(any());
    }
}