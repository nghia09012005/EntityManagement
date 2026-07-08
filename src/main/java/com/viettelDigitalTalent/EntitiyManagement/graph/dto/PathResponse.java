package com.viettelDigitalTalent.EntitiyManagement.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PathResponse {
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private boolean found;
    private int pathCount;
    private int shortestLength;  // số hop của path ngắn nhất
    private List<List<String>> paths;

    public PathResponse(List<NodeDto> nodes,
                        List<EdgeDto> edges,
                        boolean found,
                        int pathCount,
                        int shortestLength) {
        this(nodes, edges, found, pathCount, shortestLength, List.of());
    }
}
