package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.controller.GraphController;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GraphController.class)
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @Test
    void listEntitiesReturnsUsers() throws Exception {
        Map<String, Object> props = Map.of("username", "admin");
        when(neo4jClient.query(anyString()).fetch().all())
                .thenReturn(List.of(Map.of("props", props)));

        mockMvc.perform(get("/api/graph/entities/user")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("User"))
                .andExpect(jsonPath("$[0].properties.username").value("admin"));
    }

    @Test
    void listEntitiesReturnsBadRequestForUnknownType() throws Exception {
        mockMvc.perform(get("/api/graph/entities/unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNeighborsReturnsGraphResponse() throws Exception {
        Map<String, Object> relProps = Map.of("count", 3L, "firstSeen", "2026-06-18T10:00:00");
        Map<String, Object> row = Map.of(
            "srcLabel", "User",
            "srcProps", Map.of("username", "admin"),
            "relType",  "LOGGED_IN_TO",
            "relProps",  relProps,
            "tgtLabel", "Host",
            "tgtProps", Map.of("hostname", "WIN-PC01")
        );

        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(row));

        mockMvc.perform(get("/api/graph/user/admin/neighbors")
                .param("hops", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void getNeighborsBadRequestForUnknownLabel() throws Exception {
        mockMvc.perform(get("/api/graph/unknown/admin/neighbors"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNeighborsReturnsEmptyGraphWhenNoRelations() throws Exception {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/graph/ip/1.2.3.4/neighbors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.edges").isEmpty());
    }
}
