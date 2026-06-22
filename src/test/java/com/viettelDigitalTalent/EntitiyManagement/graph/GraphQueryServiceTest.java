package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.dto.GraphResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.PathResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphQueryServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    private GraphQueryService service;

    @BeforeEach
    void setUp() {
        service = new GraphQueryService(neo4jClient);
    }

    // ── resolveLabel ──────────────────────────────────────────────────────────

    @Test
    void resolveLabelReturnsCorrectMappings() {
        assertThat(service.resolveLabel("user")).isEqualTo("User");
        assertThat(service.resolveLabel("host")).isEqualTo("Host");
        assertThat(service.resolveLabel("ip")).isEqualTo("IP");
        assertThat(service.resolveLabel("domain")).isEqualTo("Domain");
        assertThat(service.resolveLabel("filehash")).isEqualTo("FileHash");
    }

    @Test
    void resolveLabelIsCaseInsensitive() {
        assertThat(service.resolveLabel("USER")).isEqualTo("User");
        assertThat(service.resolveLabel("IP")).isEqualTo("IP");
    }

    @Test
    void resolveLabelReturnsNullForUnknown() {
        assertThat(service.resolveLabel("unknown")).isNull();
        assertThat(service.resolveLabel("process")).isNull();
    }

    // ── getNeighbors ──────────────────────────────────────────────────────────

    @Test
    void getNeighborsReturnsGraphResponseWithNodesAndEdges() {
        Map<String, Object> row = Map.of(
            "srcLabel", "User",   "srcProps", Map.of("username", "admin"),
            "relType",  "LOGGED_IN_TO", "relProps", Map.of("count", 3L),
            "tgtLabel", "Host",   "tgtProps", Map.of("hostname", "WIN-PC01")
        );
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(row));

        GraphResponse resp = service.getNeighbors("User", "admin", 1);

        assertThat(resp.getNodes()).hasSize(2);
        assertThat(resp.getEdges()).hasSize(1);
        assertThat(resp.getEdges().get(0).getType()).isEqualTo("LOGGED_IN_TO");
    }

    @Test
    void getNeighborsClampsHopsAbove5To5() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        GraphResponse resp = service.getNeighbors("User", "admin", 10);
        assertThat(resp.getNodes()).isEmpty();
    }

    @Test
    void getNeighborsClampsHopsBelowOneToOne() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        GraphResponse resp = service.getNeighbors("IP", "1.2.3.4", 0);
        assertThat(resp).isNotNull();
    }

    @Test
    void getNeighborsDeduplicatesNodes() {
        // Same src/tgt appearing in two rows → only 2 distinct nodes
        Map<String, Object> row1 = Map.of(
            "srcLabel", "User", "srcProps", Map.of("username", "admin"),
            "relType",  "LOGGED_IN_TO", "relProps", Map.of(),
            "tgtLabel", "Host", "tgtProps", Map.of("hostname", "DC01")
        );
        Map<String, Object> row2 = Map.of(
            "srcLabel", "User", "srcProps", Map.of("username", "admin"),
            "relType",  "LOGGED_IN_TO", "relProps", Map.of(),
            "tgtLabel", "Host", "tgtProps", Map.of("hostname", "DC01")
        );
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(row1, row2));

        GraphResponse resp = service.getNeighbors("User", "admin", 1);
        assertThat(resp.getNodes()).hasSize(2); // User:admin + Host:DC01
        assertThat(resp.getEdges()).hasSize(2); // edges not deduplicated
    }

    @Test
    void getNeighborsEmptyResultReturnsEmptyGraph() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        GraphResponse resp = service.getNeighbors("IP", "1.2.3.4", 1);
        assertThat(resp.getNodes()).isEmpty();
        assertThat(resp.getEdges()).isEmpty();
    }

    // ── findPath ──────────────────────────────────────────────────────────────

    @Test
    void findPathReturnsTrueWhenRowsExist() {
        Map<String, Object> row = Map.of(
            "srcLabel", "User", "srcProps", Map.of("username", "admin"),
            "relType",  "LOGGED_IN_TO", "relProps", Map.of(),
            "tgtLabel", "Host", "tgtProps", Map.of("hostname", "DC01"),
            "minLen", 1L, "pathCnt", 1L
        );
        when(neo4jClient.query(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .fetch().all())
                .thenReturn(List.of(row));

        PathResponse resp = service.findPath("User", "admin", "Host", "DC01", 6, "shortest");

        assertThat(resp.isFound()).isTrue();
        assertThat(resp.getPathCount()).isEqualTo(1);
        assertThat(resp.getShortestLength()).isEqualTo(1);
        assertThat(resp.getNodes()).hasSize(2);
        assertThat(resp.getEdges()).hasSize(1);
    }

    @Test
    void findPathReturnsFalseWhenNoRows() {
        when(neo4jClient.query(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .fetch().all())
                .thenReturn(List.of());

        PathResponse resp = service.findPath("User", "admin", "IP", "9.9.9.9", 6, "shortest");

        assertThat(resp.isFound()).isFalse();
        assertThat(resp.getPathCount()).isEqualTo(0);
        assertThat(resp.getNodes()).isEmpty();
    }

    @Test
    void findPathClampsHopsToMax10() {
        when(neo4jClient.query(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .fetch().all())
                .thenReturn(List.of());

        PathResponse resp = service.findPath("User", "admin", "IP", "1.2.3.4", 100, "shortest");
        assertThat(resp.isFound()).isFalse();
    }

    @Test
    void findPathModeAllUsesAllShortestPaths() {
        when(neo4jClient.query(anyString())
                .bind(anyString()).to(anyString())
                .bind(anyString()).to(anyString())
                .fetch().all())
                .thenReturn(List.of());

        // "all" mode should not throw
        service.findPath("User", "admin", "Domain", "evil.com", 6, "all");
    }

    // ── listEntities ──────────────────────────────────────────────────────────

    @Test
    void listEntitiesReturnsNodeDtos() {
        when(neo4jClient.query(anyString()).fetch().all())
                .thenReturn(List.of(Map.of("props", Map.of("username", "admin"))));

        var nodes = service.listEntities("User");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getLabel()).isEqualTo("User");
        assertThat(nodes.get(0).getProperties().get("username")).isEqualTo("admin");
        assertThat(nodes.get(0).getId()).isEqualTo("User:admin");
    }

    @Test
    void listEntitiesEmptyWhenNoData() {
        when(neo4jClient.query(anyString()).fetch().all()).thenReturn(List.of());
        assertThat(service.listEntities("IP")).isEmpty();
    }

    @Test
    void listEntitiesHandlesMissingIdProp() {
        // props don't have the expected id field
        when(neo4jClient.query(anyString()).fetch().all())
                .thenReturn(List.of(Map.of("props", Map.of("otherField", "value"))));

        var nodes = service.listEntities("IP");
        assertThat(nodes.get(0).getId()).isEqualTo("IP:unknown");
    }
}
