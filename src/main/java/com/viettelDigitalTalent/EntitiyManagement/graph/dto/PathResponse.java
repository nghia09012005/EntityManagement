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
}
