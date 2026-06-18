package com.viettelDigitalTalent.EntitiyManagement.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class EdgeDto {
    private String from;
    private String to;
    private String type;
    private Map<String, Object> properties;
}
