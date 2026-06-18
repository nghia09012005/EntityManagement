package com.viettelDigitalTalent.EntitiyManagement.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GraphResponse {
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
}
