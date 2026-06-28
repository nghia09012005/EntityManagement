package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfEndpoint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCSF class_uid = 4001 (Network Activity), category_uid = 4.
 * activity_id: 1=Open  2=Close  6=Traffic
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("NETWORK")
public class NetworkEvent extends BaseEvent {

    @JsonProperty("src_endpoint") private OcsfEndpoint srcEndpoint;
    @JsonProperty("dst_endpoint") private OcsfEndpoint dstEndpoint;

    // ── Convenience bridge ────────────────────────────────────────────────────

    @JsonIgnore public String getSrcIp() {
        return srcEndpoint != null ? srcEndpoint.getIp() : null;
    }
    public void setSrcIp(String ip) {
        if (srcEndpoint == null) srcEndpoint = new OcsfEndpoint();
        srcEndpoint.setIp(ip);
    }

    @JsonIgnore public String getDstIp() {
        return dstEndpoint != null ? dstEndpoint.getIp() : null;
    }
    public void setDstIp(String ip) {
        if (dstEndpoint == null) dstEndpoint = new OcsfEndpoint();
        dstEndpoint.setIp(ip);
    }

    @JsonIgnore public String getDstDomain() {
        return dstEndpoint != null ? dstEndpoint.getDomain() : null;
    }
    public void setDstDomain(String domain) {
        if (dstEndpoint == null) dstEndpoint = new OcsfEndpoint();
        dstEndpoint.setDomain(domain);
    }

    @JsonIgnore public int getDstPort() {
        return dstEndpoint != null && dstEndpoint.getPort() != null ? dstEndpoint.getPort() : 0;
    }
    public void setDstPort(int port) {
        if (dstEndpoint == null) dstEndpoint = new OcsfEndpoint();
        dstEndpoint.setPort(port);
    }
}