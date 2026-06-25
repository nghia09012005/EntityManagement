package com.viettelDigitalTalent.EntitiyManagement.ingestion;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.IngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.viettelDigitalTalent.EntitiyManagement.ingestion.controller.LogIngestionController.class)
@WithMockUser
class LogIngestionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  IngestionService ingestionService;
    @MockBean  JwtService jwtService;

    @Test
    void returns202ForValidLog() throws Exception {
        String log = "{\"user\":\"admin\",\"ip\":\"1.2.3.4\"}";

        mockMvc.perform(post("/api/v1/ingest").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(log))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Log queued"));

        verify(ingestionService, times(1)).sendToQueue(eq(log), anyString());
    }

    @Test
    void returns202ForAlertEvent() throws Exception {
        String log = "{\"eventType\":\"ALERT\",\"alertName\":\"Brute Force\"}";

        mockMvc.perform(post("/api/v1/ingest").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(log))
                .andExpect(status().isAccepted());

        verify(ingestionService).sendToQueue(eq(log), anyString());
    }

    @Test
    void returns202ForNetworkEvent() throws Exception {
        String log = "{\"eventType\":\"NETWORK\",\"srcIp\":\"10.0.0.1\",\"dstIp\":\"8.8.8.8\"}";

        mockMvc.perform(post("/api/v1/ingest").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(log))
                .andExpect(status().isAccepted());

        verify(ingestionService).sendToQueue(eq(log), anyString());
    }

    @Test
    void returns202WithMockUser() throws Exception {
        mockMvc.perform(post("/api/v1/ingest").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("test"))
                .andExpect(status().isAccepted());
    }
}
