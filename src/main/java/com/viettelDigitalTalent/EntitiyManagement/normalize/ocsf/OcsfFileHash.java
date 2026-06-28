package com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** algorithm_id: 1=MD5  2=SHA-1  3=SHA-256  4=SHA-512 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcsfFileHash {
    @JsonProperty("algorithm_id") private int    algorithmId;
    @JsonProperty("algorithm")    private String algorithm;
    @JsonProperty("value")        private String value;
}