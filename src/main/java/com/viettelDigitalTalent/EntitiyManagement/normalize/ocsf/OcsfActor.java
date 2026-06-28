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
public class OcsfActor {
    @JsonProperty("user")    private OcsfUser    user;
    @JsonProperty("process") private OcsfProcess process;
}