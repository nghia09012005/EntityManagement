package com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcsfProcess {
    @JsonProperty("name")     private String    name;
    @JsonProperty("cmd_line") private String    cmdLine;
    @JsonProperty("pid")      private Integer   pid;
    @JsonProperty("file")     private OcsfFile  file;
}