package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfActor;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfEndpoint;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCSF class_uid = 3002 (Authentication), category_uid = 3 (Identity & Access Management).
 * activity_id: 1=Logon  2=Logoff  3=Authentication Ticket
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("AUTHENTICATION")
public class AuthenticationEvent extends BaseEvent {

    @JsonProperty("actor")        private OcsfActor   actor;
    @JsonProperty("src_endpoint") private OcsfEndpoint srcEndpoint;
    @JsonProperty("dst_endpoint") private OcsfEndpoint dstEndpoint;

    // ── Convenience bridge ────────────────────────────────────────────────────

    @JsonIgnore public String getUsername() {
        return actor != null && actor.getUser() != null ? actor.getUser().getName() : null;
    }
    public void setUsername(String name) {
        if (actor == null) actor = new OcsfActor();
        if (actor.getUser() == null) actor.setUser(new OcsfUser());
        actor.getUser().setName(name);
    }

    @JsonIgnore public String getIpAddress() {
        return srcEndpoint != null ? srcEndpoint.getIp() : null;
    }
    public void setIpAddress(String ip) {
        if (srcEndpoint == null) srcEndpoint = new OcsfEndpoint();
        srcEndpoint.setIp(ip);
    }

    @JsonIgnore public String getWorkstation() {
        return dstEndpoint != null ? dstEndpoint.getHostname() : null;
    }
    public void setWorkstation(String hostname) {
        if (dstEndpoint == null) dstEndpoint = new OcsfEndpoint();
        dstEndpoint.setHostname(hostname);
    }

    /** Maps success flag → OCSF status_id (1=Success, 2=Failure) */
    @JsonIgnore public boolean isSuccess() { return getStatusId() == 1; }
    public void setSuccess(boolean success) {
        setStatusId(success ? 1 : 2);
        setStatus(success ? "Success" : "Failure");
    }
}