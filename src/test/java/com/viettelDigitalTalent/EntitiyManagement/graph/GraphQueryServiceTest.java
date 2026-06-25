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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class GraphQueryServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    private GraphQueryService service;

    private static final String testTenantId = "test-tenant";

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
    void resolveLabelReturnsNewEntityTypes() {
        assertThat(service.resolveLabel("url")).isEqualTo("Url");
        assertThat(service.resolveLabel("process")).isEqualTo("Process");
        assertThat(service.resolveLabel("cloudresource")).isEqualTo("CloudResource");
        assertThat(service.resolveLabel("email")).isEqualTo("Email");
        assertThat(service.resolveLabel("cve")).isEqualTo("Cve");
    }

    @Test
    void resolveLabelIsCaseInsensitive() {
        assertThat(service.resolveLabel("USER")).isEqualTo("User");
        assertThat(service.resolveLabel("IP")).isEqualTo("IP");
        assertThat(service.resolveLabel("CVE")).isEqualTo("Cve");
    }

    @Test
    void resolveLabelReturnsNullForUnknown() {
        assertThat(service.resolveLabel("unknown")).isNull();
        assertThat(service.resolveLabel("tenant")).isNull();
    }

    // ── getNeighbors ──────────────────────────────────────────────────────────

    @Test
    void getNeighborsReturnsGraphResponseWithNodesAndEdges() {
        Map<String, Object> row = Map.of(
                "srcLabel", "User",   "srcProps", Map.of("username", "admin"),
                "relType",  "LOGGED_IN_TO", "relProps", Map.of("count", 3L),
                "tgtLabel", "Host",   "tgtProps", Map.of("hostname", "WIN-PC01")
        );

        // CHỈ DÙNG 2 LỆNH BIND ĐỂ KHỚP VỚI SERVICE
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .bind(any()).to("value")
                .fetch().all())
                .thenReturn(List.of(row));

        GraphResponse resp = service.getNeighbors("User", "admin", 1, testTenantId);

        assertThat(resp.getNodes()).hasSize(2);
        assertThat(resp.getEdges()).hasSize(1);
        assertThat(resp.getEdges().get(0).getType()).isEqualTo("LOGGED_IN_TO");
    }

    @Test
    void getNeighborsClampsHopsAbove5To5() {
        // SỬA: Chỉ mock đúng 2 lệnh bind() như logic trong GraphQueryService.java
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .bind(any()).to("value")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        GraphResponse resp = service.getNeighbors("User", "admin", 10, testTenantId);

        // Kiểm tra kết quả
        assertThat(resp.getNodes()).isEmpty();
    }

    @Test
    void getNeighborsClampsHopsBelowOneToOne() {
        // SỬA: Chỉ mock đúng 2 lệnh bind() để khớp với logic trong GraphQueryService.java
        // (Lưu ý: tham số 1 là 'tenantId', tham số 2 là 'value')
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .bind(any()).to("value")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        GraphResponse resp = service.getNeighbors("IP", "1.2.3.4", 0, testTenantId);

        // Kiểm tra kết quả
        assertThat(resp).isNotNull();
        assertThat(resp.getNodes()).isEmpty();
    }

    @Test
    void getNeighborsDeduplicatesNodes() {
        Map<String, Object> row1 = Map.of(
                "srcLabel", "User", "srcProps", Map.of("username", "admin"),
                "relType",  "LOGGED_IN_TO", "relProps", Map.of(),
                "tgtLabel", "Host", "tgtProps", Map.of("hostname", "DC01")
        );
        // Row2 giống hệt row1 để kiểm tra khả năng khử trùng lặp (deduplication)
        Map<String, Object> row2 = row1;

        // SỬA: Chỉ mock đúng 2 lệnh bind() như logic trong GraphQueryService
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .bind(any()).to("value")
                .fetch().all())
                .thenReturn(List.of(row1, row2));

        // Truyền thêm testTenantId vào service
        GraphResponse resp = service.getNeighbors("User", "admin", 1, testTenantId);

        // Kết quả: Mặc dù trả về 2 dòng dữ liệu giống nhau từ DB,
        // danh sách node phải được khử trùng lặp (chỉ còn 2 node: User:admin và Host:DC01)
        assertThat(resp.getNodes()).hasSize(2);
        // Các cạnh (edges) trong logic của bạn không khử trùng lặp
        assertThat(resp.getEdges()).hasSize(2);
    }

    @Test
    void getNeighborsEmptyResultReturnsEmptyGraph() {
        // SỬA: Chỉ mock đúng 2 lệnh bind() để khớp với logic trong GraphQueryService
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .bind(any()).to("value")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        GraphResponse resp = service.getNeighbors("IP", "1.2.3.4", 1, testTenantId);

        // Kiểm tra kết quả
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

        // SỬA: Chỉ mock đúng 3 lệnh bind() như code thực tế
        when(neo4jClient.query(anyString())
                .bind(any()).to("fromValue")
                .bind(any()).to("toValue")
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of(row));

        // Truyền testTenantId vào service
        PathResponse resp = service.findPath("User", "admin", "Host", "DC01", 6, "shortest", testTenantId);

        assertThat(resp.isFound()).isTrue();
        assertThat(resp.getPathCount()).isEqualTo(1);
        assertThat(resp.getShortestLength()).isEqualTo(1);
        assertThat(resp.getNodes()).hasSize(2);
        assertThat(resp.getEdges()).hasSize(1);
    }

    @Test
    void findPathReturnsFalseWhenNoRows() {
        // SỬA: Chỉ mock đúng 3 lệnh bind() như code thực tế trong Service
        when(neo4jClient.query(anyString())
                .bind(any()).to("fromValue")
                .bind(any()).to("toValue")
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        PathResponse resp = service.findPath("User", "admin", "IP", "9.9.9.9", 6, "shortest", testTenantId);

        assertThat(resp.isFound()).isFalse();
        assertThat(resp.getPathCount()).isEqualTo(0);
        assertThat(resp.getNodes()).isEmpty();
    }

    @Test
    void findPathClampsHopsToMax10() {
        // SỬA: Chỉ mock đúng 3 lệnh bind() như code thực tế trong Service
        when(neo4jClient.query(anyString())
                .bind(any()).to("fromValue")
                .bind(any()).to("toValue")
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        // Hops = 100 sẽ bị service tự động "clamp" về 10
        PathResponse resp = service.findPath("User", "admin", "IP", "1.2.3.4", 100, "shortest", testTenantId);

        assertThat(resp.isFound()).isFalse();
    }

    @Test
    void findPathModeAllUsesAllShortestPaths() {
        // SỬA: Chỉ mock đúng 3 lệnh bind() như code thực tế trong Service
        when(neo4jClient.query(anyString())
                .bind(any()).to("fromValue")
                .bind(any()).to("toValue")
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        // Test này kiểm tra việc truyền mode "all" không gây lỗi logic trong service
        service.findPath("User", "admin", "Domain", "evil.com", 6, "all", testTenantId);

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    // ── listEntities ──────────────────────────────────────────────────────────

    @Test
    void listEntitiesReturnsNodeDtos() {
        // SỬA: Chỉ mock đúng 1 lệnh bind() cho "tenantId" như trong service
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of(Map.of("props", Map.of("username", "admin"))));

        // Truyền testTenantId vào service
        var nodes = service.listEntities("User", testTenantId);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getLabel()).isEqualTo("User");
        assertThat(nodes.get(0).getProperties().get("username")).isEqualTo("admin");
        assertThat(nodes.get(0).getId()).isEqualTo("User:admin");
    }

    @Test
    void listEntitiesEmptyWhenNoData() {
        // SỬA: Chỉ mock 1 lệnh bind() cho "tenantId" để khớp với logic Service
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of());

        // Cập nhật: Truyền thêm testTenantId vào service
        assertThat(service.listEntities("IP", testTenantId)).isEmpty();
    }

    @Test
    void listEntitiesHandlesMissingIdProp() {
        // SỬA: Chỉ mock 1 lệnh bind() cho "tenantId"
        when(neo4jClient.query(anyString())
                .bind(any()).to("tenantId")
                .fetch().all())
                .thenReturn(List.of(Map.of("props", Map.of("otherField", "value"))));

        // Cập nhật: Truyền thêm testTenantId vào service
        var nodes = service.listEntities("IP", testTenantId);

        assertThat(nodes.get(0).getId()).isEqualTo("IP:unknown");
    }

    // ── tenantLabel ───────────────────────────────────────────────────────────

    @Test
    void tenantLabel_returnsTenantIdAsIs() {
        String id = "abc-123-def-456";
        assertThat(GraphQueryService.tenantLabel(id)).isEqualTo(id);
    }

    @Test
    void tenantLabel_nullReturnsNull() {
        assertThat(GraphQueryService.tenantLabel(null)).isNull();
    }

    @Test
    void tenantLabel_blankReturnsBlank() {
        assertThat(GraphQueryService.tenantLabel("")).isEqualTo("");
    }

    @Test
    void tenantLabel_uuidReturnedUnchanged() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(GraphQueryService.tenantLabel(uuid)).isEqualTo(uuid);
    }
}
