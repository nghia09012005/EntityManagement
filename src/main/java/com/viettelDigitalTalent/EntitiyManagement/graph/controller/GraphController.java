package com.viettelDigitalTalent.EntitiyManagement.graph.controller;

import com.viettelDigitalTalent.EntitiyManagement.graph.dto.EdgeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.GraphResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.NodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final Neo4jClient neo4jClient;

    private static final Map<String, String> LABEL_MAP = Map.of(
        "user",     "User",
        "host",     "Host",
        "ip",       "IP",
        "domain",   "Domain",
        "filehash", "FileHash"
    );

    private static final Map<String, String> ID_PROP = Map.of(
        "User",     "username",
        "Host",     "hostname",
        "IP",       "address",
        "Domain",   "name",
        "FileHash", "hash"
    );

    /**
     * GET /api/graph/{label}/{value}/neighbors?hops=1
     *
     * label: user | host | ip | domain | filehash
     * value: e.g. "admin", "192.168.1.1"
     * hops:  1 or 2 (default 1)
     */
    @GetMapping("/{label}/{value}/neighbors")
    public ResponseEntity<GraphResponse> getNeighbors(
            @PathVariable String label,
            @PathVariable String value,
            @RequestParam(defaultValue = "1") int hops) {

        String nodeLabel = LABEL_MAP.get(label.toLowerCase());
        if (nodeLabel == null) {
            return ResponseEntity.badRequest().build();
        }
        String idProp = ID_PROP.get(nodeLabel);
        int maxHops = Math.min(Math.max(hops, 1), 2);

        String cypher = String.format("""
                MATCH path = (n:%s {%s: $value})-[*1..%d]-(m)
                UNWIND relationships(path) AS r
                WITH DISTINCT r, startNode(r) AS src, endNode(r) AS tgt
                RETURN
                  labels(src)[0] AS srcLabel, properties(src) AS srcProps,
                  type(r)        AS relType,  properties(r)   AS relProps,
                  labels(tgt)[0] AS tgtLabel, properties(tgt) AS tgtProps
                """, nodeLabel, idProp, maxHops);

        Collection<Map<String, Object>> rows = neo4jClient
                .query(cypher)
                .bind(value).to("value")
                .fetch()
                .all();

        Map<String, NodeDto> nodeMap = new LinkedHashMap<>();
        List<EdgeDto> edges = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String srcLabel = (String) row.get("srcLabel");
            Map<String, Object> srcProps = castMap(row.get("srcProps"));
            String tgtLabel = (String) row.get("tgtLabel");
            Map<String, Object> tgtProps = castMap(row.get("tgtProps"));
            String relType  = (String) row.get("relType");
            Map<String, Object> relProps = castMap(row.get("relProps"));

            String srcId = nodeId(srcLabel, srcProps);
            String tgtId = nodeId(tgtLabel, tgtProps);

            nodeMap.putIfAbsent(srcId, new NodeDto(srcId, srcLabel, srcProps));
            nodeMap.putIfAbsent(tgtId, new NodeDto(tgtId, tgtLabel, tgtProps));
            edges.add(new EdgeDto(srcId, tgtId, relType, relProps));
        }

        return ResponseEntity.ok(new GraphResponse(new ArrayList<>(nodeMap.values()), edges));
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
