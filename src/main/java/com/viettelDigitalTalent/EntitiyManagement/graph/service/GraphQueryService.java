package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.graph.dto.EdgeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.GraphResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.NodeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.PathResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphQueryService {

    private final Neo4jClient neo4jClient;

    public static final Map<String, String> LABEL_MAP = Map.of(
        "user",     "User",
        "host",     "Host",
        "ip",       "IP",
        "domain",   "Domain",
        "filehash", "FileHash"
    );

    public static final Map<String, String> ID_PROP = Map.of(
        "User",     "username",
        "Host",     "hostname",
        "IP",       "address",
        "Domain",   "name",
        "FileHash", "hash"
    );

    /** Trả về null nếu key không hợp lệ — controller dùng để trả 400. */
    public String resolveLabel(String key) {
        return LABEL_MAP.get(key.toLowerCase());
    }

    // ── Neighbors ──────────────────────────────────────────────────────────────

    public GraphResponse getNeighbors(String nodeLabel, String value, int hops) {
        String idProp  = ID_PROP.get(nodeLabel);
        int maxHops    = Math.min(Math.max(hops, 1), 5);
        int limit      = switch (maxHops) {
            case 1 -> 200;
            case 2 -> 500;
            default -> 1000;
        };

        String cypher = String.format("""
                MATCH path = (n:%s {%s: $value})-[*1..%d]-(m)
                UNWIND relationships(path) AS r
                WITH DISTINCT r, startNode(r) AS src, endNode(r) AS tgt
                RETURN
                  labels(src)[0] AS srcLabel, properties(src) AS srcProps,
                  type(r)        AS relType,  properties(r)   AS relProps,
                  labels(tgt)[0] AS tgtLabel, properties(tgt) AS tgtProps
                LIMIT %d
                """, nodeLabel, idProp, maxHops, limit);

        Collection<Map<String, Object>> rows = neo4jClient
                .query(cypher)
                .bind(value).to("value")
                .fetch()
                .all();

        return buildGraphResponse(rows);
    }

    // ── Path finding ───────────────────────────────────────────────────────────

    public PathResponse findPath(String fromLabel, String fromValue,
                                 String toLabel,   String toValue,
                                 int maxHops, String mode) {
        String fromIdProp  = ID_PROP.get(fromLabel);
        String toIdProp    = ID_PROP.get(toLabel);
        int    clampedHops = Math.min(Math.max(maxHops, 1), 10);
        String pathFn      = "all".equalsIgnoreCase(mode) ? "allShortestPaths" : "shortestPath";

        String cypher = String.format("""
                MATCH (src:%s {%s: $fromValue}), (dst:%s {%s: $toValue})
                MATCH path = %s((src)-[*1..%d]-(dst))
                WITH collect(path) AS paths
                UNWIND paths AS p
                WITH p, length(p) AS pathLen
                ORDER BY pathLen
                UNWIND relationships(p) AS r
                WITH DISTINCT r, startNode(r) AS s, endNode(r) AS t,
                     min(pathLen) AS minLen, count(DISTINCT p) AS pathCnt
                RETURN
                  labels(s)[0] AS srcLabel, properties(s) AS srcProps,
                  type(r)      AS relType,  properties(r)  AS relProps,
                  labels(t)[0] AS tgtLabel, properties(t)  AS tgtProps,
                  minLen, pathCnt
                """, fromLabel, fromIdProp, toLabel, toIdProp, pathFn, clampedHops);

        Collection<Map<String, Object>> rows = neo4jClient
                .query(cypher)
                .bind(fromValue).to("fromValue")
                .bind(toValue).to("toValue")
                .fetch()
                .all();

        if (rows.isEmpty()) {
            return new PathResponse(List.of(), List.of(), false, 0, 0);
        }

        Map<String, NodeDto> nodeMap = new LinkedHashMap<>();
        List<EdgeDto> edges = new ArrayList<>();
        int minLen = 0, pathCnt = 0;

        for (Map<String, Object> row : rows) {
            String srcLabel  = (String) row.get("srcLabel");
            Map<String, Object> srcProps = castMap(row.get("srcProps"));
            String tgtLabel  = (String) row.get("tgtLabel");
            Map<String, Object> tgtProps = castMap(row.get("tgtProps"));
            String relType   = (String) row.get("relType");
            Map<String, Object> relProps = castMap(row.get("relProps"));

            String srcId = nodeId(srcLabel, srcProps);
            String tgtId = nodeId(tgtLabel, tgtProps);

            nodeMap.putIfAbsent(srcId, new NodeDto(srcId, srcLabel, srcProps));
            nodeMap.putIfAbsent(tgtId, new NodeDto(tgtId, tgtLabel, tgtProps));
            edges.add(new EdgeDto(srcId, tgtId, relType, relProps));

            if (row.get("minLen") instanceof Number n) minLen  = n.intValue();
            if (row.get("pathCnt") instanceof Number n) pathCnt = n.intValue();
        }

        return new PathResponse(new ArrayList<>(nodeMap.values()), edges, true, pathCnt, minLen);
    }

    // ── Entity list ────────────────────────────────────────────────────────────

    public List<NodeDto> listEntities(String nodeLabel) {
        String idProp = ID_PROP.get(nodeLabel);

        Collection<Map<String, Object>> rows = neo4jClient
                .query(String.format("MATCH (n:%s) RETURN properties(n) AS props", nodeLabel))
                .fetch()
                .all();

        return rows.stream()
                .map(row -> {
                    Map<String, Object> props = castMap(row.get("props"));
                    Object val = props.get(idProp);
                    String id = nodeLabel + ":" + (val != null ? val.toString() : "unknown");
                    return new NodeDto(id, nodeLabel, props);
                })
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private GraphResponse buildGraphResponse(Collection<Map<String, Object>> rows) {
        Map<String, NodeDto> nodeMap = new LinkedHashMap<>();
        List<EdgeDto> edges = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String srcLabel  = (String) row.get("srcLabel");
            Map<String, Object> srcProps = castMap(row.get("srcProps"));
            String tgtLabel  = (String) row.get("tgtLabel");
            Map<String, Object> tgtProps = castMap(row.get("tgtProps"));
            String relType   = (String) row.get("relType");
            Map<String, Object> relProps = castMap(row.get("relProps"));

            String srcId = nodeId(srcLabel, srcProps);
            String tgtId = nodeId(tgtLabel, tgtProps);

            nodeMap.putIfAbsent(srcId, new NodeDto(srcId, srcLabel, srcProps));
            nodeMap.putIfAbsent(tgtId, new NodeDto(tgtId, tgtLabel, tgtProps));
            edges.add(new EdgeDto(srcId, tgtId, relType, relProps));
        }

        return new GraphResponse(new ArrayList<>(nodeMap.values()), edges);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        if (obj instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return new HashMap<>();
    }

    private String nodeId(String label, Map<String, Object> props) {
        String key = ID_PROP.getOrDefault(label, "id");
        Object val = props.get(key);
        return label + ":" + (val != null ? val.toString() : "unknown");
    }
}
