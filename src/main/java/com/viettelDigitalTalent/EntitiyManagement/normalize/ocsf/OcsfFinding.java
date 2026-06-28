package com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcsfFinding {
    @JsonProperty("title") private String       title;
    @JsonProperty("uid")   private String       uid;
    @JsonProperty("desc")  private String       desc;
    @JsonProperty("types") private List<String> types;
}