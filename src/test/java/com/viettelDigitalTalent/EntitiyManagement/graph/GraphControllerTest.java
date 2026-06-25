package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.controller.GraphController;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.EdgeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.GraphResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.NodeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.PathResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphQueryService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GraphController.class)
@WithMockUser
class GraphControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  GraphQueryService graphQueryService;
    @MockBean  JwtService jwtService;

    // ── listEntities ─────────────────────────────────────────────────────────

    @Test
    void listEntitiesReturnsUsers() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.listEntities(eq("User"), anyString())).thenReturn(List.of(
                new NodeDto("User:admin", "User", Map.of("username", "admin"))
        ));

        mockMvc.perform(get("/api/graph/entities/user").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("User"))
                .andExpect(jsonPath("$[0].properties.username").value("admin"));
    }

    @Test
    void listEntitiesBadRequestForUnknownType() throws Exception {
        when(graphQueryService.resolveLabel("unknown")).thenReturn(null);
        mockMvc.perform(get("/api/graph/entities/unknown"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listEntitiesEmptyReturnsEmptyArray() throws Exception {
        when(graphQueryService.resolveLabel("ip")).thenReturn("IP");
        when(graphQueryService.listEntities(eq("IP"), anyString())).thenReturn(List.of());
        mockMvc.perform(get("/api/graph/entities/ip").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── getNeighbors ─────────────────────────────────────────────────────────

    @Test
    void getNeighborsReturnsGraphResponse() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.getNeighbors(eq("User"), eq("admin"), anyInt(), anyString()))
                .thenReturn(new GraphResponse(
                        List.of(new NodeDto("User:admin", "User", Map.of("username", "admin")),
                                new NodeDto("Host:WIN-PC01", "Host", Map.of("hostname", "WIN-PC01"))),
                        List.of(new EdgeDto("User:admin", "Host:WIN-PC01", "LOGGED_IN_TO", Map.of()))
                ));

        mockMvc.perform(get("/api/graph/user/admin/neighbors").param("hops", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].label").value("User"))
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void getNeighborsBadRequestForUnknownLabel() throws Exception {
        when(graphQueryService.resolveLabel("unknown")).thenReturn(null);
        mockMvc.perform(get("/api/graph/unknown/admin/neighbors"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNeighborsEmptyGraphWhenNoRelations() throws Exception {
        when(graphQueryService.resolveLabel("ip")).thenReturn("IP");
        when(graphQueryService.getNeighbors(eq("IP"), eq("1.2.3.4"), anyInt(), anyString()))
                .thenReturn(new GraphResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/graph/ip/1.2.3.4/neighbors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.edges").isEmpty());
    }

    @Test
    void getNeighborsDefaultHopsIs1() throws Exception {
        when(graphQueryService.resolveLabel("host")).thenReturn("Host");
        when(graphQueryService.getNeighbors(eq("Host"), eq("DC01"), eq(1), anyString()))
                .thenReturn(new GraphResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/graph/host/DC01/neighbors"))
                .andExpect(status().isOk());
    }

    // ── findPath ─────────────────────────────────────────────────────────────

    @Test
    void findPathReturnsFoundPath() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.resolveLabel("host")).thenReturn("Host");
        when(graphQueryService.findPath(eq("User"), eq("admin"), eq("Host"), eq("DC01"),
                anyInt(), anyString(), anyString()))
                .thenReturn(new PathResponse(
                        List.of(new NodeDto("User:admin", "User", Map.of()),
                                new NodeDto("Host:DC01", "Host", Map.of())),
                        List.of(new EdgeDto("User:admin", "Host:DC01", "LOGGED_IN_TO", Map.of())),
                        true, 1, 1
                ));

        mockMvc.perform(get("/api/graph/path")
                        .param("fromType", "user").param("fromValue", "admin")
                        .param("toType",   "host").param("toValue",   "DC01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.pathCount").value(1));
    }

    @Test
    void findPathReturnsFalseWhenNoPath() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.resolveLabel("ip")).thenReturn("IP");
        when(graphQueryService.findPath(anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString()))
                .thenReturn(new PathResponse(List.of(), List.of(), false, 0, 0));

        mockMvc.perform(get("/api/graph/path")
                        .param("fromType", "user").param("fromValue", "admin")
                        .param("toType",   "ip").param("toValue",   "9.9.9.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void findPathBadRequestForUnknownFromType() throws Exception {
        when(graphQueryService.resolveLabel("unknown")).thenReturn(null);
        mockMvc.perform(get("/api/graph/path")
                .param("fromType", "unknown").param("fromValue", "x")
                .param("toType",   "ip").param("toValue",   "1.1.1.1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findPathBadRequestForUnknownToType() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.resolveLabel("unknown")).thenReturn(null);
        mockMvc.perform(get("/api/graph/path")
                .param("fromType", "user").param("fromValue", "admin")
                .param("toType",   "unknown").param("toValue", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findPathPassesModeAndHopsToService() throws Exception {
        when(graphQueryService.resolveLabel("user")).thenReturn("User");
        when(graphQueryService.resolveLabel("ip")).thenReturn("IP");
        when(graphQueryService.findPath(anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyString(), anyString()))
                .thenReturn(new PathResponse(List.of(), List.of(), false, 0, 0));

        mockMvc.perform(get("/api/graph/path")
                        .param("fromType", "user").param("fromValue", "admin")
                        .param("toType",   "ip").param("toValue",   "1.2.3.4")
                        .param("maxHops",  "4").param("mode", "all"))
                .andExpect(status().isOk());
    }
}
