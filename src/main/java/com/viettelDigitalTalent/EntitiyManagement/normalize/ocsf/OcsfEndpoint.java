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
public class OcsfEndpoint {
    @JsonProperty("ip")       private String  ip;
    @JsonProperty("hostname") private String  hostname;
    @JsonProperty("port")     private Integer port;
    @JsonProperty("domain")   private String  domain;
}