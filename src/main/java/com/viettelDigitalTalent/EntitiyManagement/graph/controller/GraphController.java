package com.viettelDigitalTalent.EntitiyManagement.graph.controller;

import com.viettelDigitalTalent.EntitiyManagement.graph.dto.GraphResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.NodeDto;
import com.viettelDigitalTalent.EntitiyManagement.graph.dto.PathResponse;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphQueryService graphQueryService;

    @GetMapping("/{label}/{value}/neighbors")
    public ResponseEntity<GraphResponse> getNeighbors(
            @PathVariable String label,
            @PathVariable String value,
            @RequestParam(defaultValue = "1") int hops,
            Authentication authentication) {

        String nodeLabel = graphQueryService.resolveLabel(label);
        if (nodeLabel == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(
                graphQueryService.getNeighbors(nodeLabel, value, hops, tenantId(authentication)));
    }

    @GetMapping("/path")
    public ResponseEntity<PathResponse> findPath(
            @RequestParam String fromType,
            @RequestParam String fromValue,
            @RequestParam String toType,
            @RequestParam String toValue,
            @RequestParam(defaultValue = "6") int maxHops,
            @RequestParam(defaultValue = "shortest") String mode,
            Authentication authentication) {

        String fromLabel = graphQueryService.resolveLabel(fromType);
        String toLabel   = graphQueryService.resolveLabel(toType);
        if (fromLabel == null || toLabel == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(
                graphQueryService.findPath(fromLabel, fromValue, toLabel, toValue, maxHops, mode,
                        tenantId(authentication)));
    }

    @GetMapping("/entities/{type}")
    public ResponseEntity<List<NodeDto>> listEntities(@PathVariable String type,
                                                      Authentication authentication) {
        String nodeLabel = graphQueryService.resolveLabel(type);
        if (nodeLabel == null) return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(graphQueryService.listEntities(nodeLabel, tenantId(authentication)));
    }

    @SuppressWarnings("unchecked")
    private String tenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}
