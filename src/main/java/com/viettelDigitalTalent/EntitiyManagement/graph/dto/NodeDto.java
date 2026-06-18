package com.viettelDigitalTalent.EntitiyManagement.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class NodeDto {
    private String id;
    private String label;
    private Map<String, Object> properties;
}
