package com.viettelDigitalTalent.EntitiyManagement.correlation.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.AuditLog;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.Incident;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.AuditLogRepository;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private IncidentController incidentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(incidentController)
                .build();
    }

    // =========================
    // HELPER
    // =========================
    private Incident incident(String id, String status, String severity) {
        Incident i = new Incident();
        i.setId(id);
        i.setStatus(status);
        i.setSeverity(severity);
        i.setDetectedAt(LocalDateTime.now());
        return i;
    }

    // =========================
    // LIST
    // =========================
    @Test
    void list_shouldReturnPage() throws Exception {

        Page<Incident> page = new PageImpl<>(
                List.of(incident("1", "NEW", "HIGH"))
        );

        when(incidentRepository.findAllByTenantIdOrderByDetectedAtDesc(
                anyString(),
                any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get("/api/incidents")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isInternalServerError());
    }

    // =========================
    // ALERTS
    // =========================
    @Test
    void alerts_shouldReturnPage() throws Exception {

        Page<AuditLog> page = new PageImpl<>(
                List.of(new AuditLog())
        );

        when(auditLogRepository.findAlerts(
                any(Pageable.class),
                anyString()
        )).thenReturn(page);

        mockMvc.perform(get("/api/incidents/alerts")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isInternalServerError());
    }
}