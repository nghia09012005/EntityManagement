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
public class OcsfFile {
    @JsonProperty("name")   private String          name;
    @JsonProperty("path")   private String          path;
    @JsonProperty("hashes") private List<OcsfFileHash> hashes;
}