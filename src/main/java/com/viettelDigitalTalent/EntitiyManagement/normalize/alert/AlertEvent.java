package com.viettelDigitalTalent.EntitiyManagement.normalize.alert;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * OCSF class_uid = 2001 (Security Finding), category_uid = 2 (Findings).
 * activity_id: 1=Create  2=Update  3=Close
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("THREAT")
public class AlertEvent extends BaseEvent {

    @JsonProperty("finding")      private OcsfFinding  finding;
    @JsonProperty("actor")        private OcsfActor    actor;
    @JsonProperty("dst_endpoint") private OcsfEndpoint dstEndpoint;
    @JsonProperty("process")      private OcsfProcess  process;

    // Extension fields (non-OCSF core, snake_case per OCSF convention)
    @JsonProperty("target_url")            private String targetUrl;
    @JsonProperty("cloud_resource_id")     private String targetCloudResourceId;
    @JsonProperty("target_email")          private String targetEmail;
    @JsonProperty("cve_uid") @JsonAlias("targetCve") private String targetCve;

    public void setTargetCve(String cve) { this.targetCve = cve; }


    // ----------------------------------src endpoint----------------------------------
    @JsonProperty("src_endpoint")
    private OcsfEndpoint srcEndpoint;

    @JsonIgnore
    public String getSourceIp() {
        return srcEndpoint != null ? srcEndpoint.getIp() : null;
    }

    private void ensureSrcEndpoint() {
        if (srcEndpoint == null)
            srcEndpoint = new OcsfEndpoint();
    }

    public void setSourceIp(String ip) {
        if (ip == null) return;
        ensureSrcEndpoint();
        srcEndpoint.setIp(ip);
    }

    @JsonIgnore public String getSourceHost() {
        return srcEndpoint != null ? srcEndpoint.getHostname() : null;
    }

    public void setSourceHost(String hostname) {
        if (hostname == null) return;
        ensureSrcEndpoint();
        srcEndpoint.setHostname(hostname);
    }

    @JsonIgnore public String getSourceDomain() {
        return srcEndpoint != null ? srcEndpoint.getDomain() : null;
    }

    public void setSourceDomain(String domain) {
        if (domain == null) return;
        ensureSrcEndpoint();
        srcEndpoint.setDomain(domain);
    }

    // -----------------------------------------------------------



    // ── Convenience bridge ────────────────────────────────────────────────────

    @JsonIgnore public String getAlertName() {
        return finding != null ? finding.getTitle() : null;
    }
    public void setAlertName(String name) {
        ensureFinding();
        finding.setTitle(name);
    }

    /** Maps string severity to OCSF severity_id (kept in BaseEvent) */
    public void setSeverity(String s) {
        setSeverityFromString(s);
    }

    @JsonIgnore public String getDescription() {
        return finding != null ? finding.getDesc() : null;
    }
    public void setDescription(String desc) {
        ensureFinding();
        finding.setDesc(desc);
        setMessage(desc);
    }

    @JsonIgnore public String getTargetIp() {
        return dstEndpoint != null ? dstEndpoint.getIp() : null;
    }
    public void setTargetIp(String ip) {
        if (ip == null) return;
        ensureDstEndpoint();
        dstEndpoint.setIp(ip);
    }

    @JsonIgnore public String getTargetUser() {
        return actor != null && actor.getUser() != null ? actor.getUser().getName() : null;
    }
    public void setTargetUser(String name) {
        if (actor == null) actor = new OcsfActor();
        if (actor.getUser() == null) actor.setUser(new OcsfUser());
        actor.getUser().setName(name);
    }

    @JsonIgnore public String getTargetHost() {
        return dstEndpoint != null ? dstEndpoint.getHostname() : null;
    }
    public void setTargetHost(String hostname) {
        ensureDstEndpoint();
        dstEndpoint.setHostname(hostname);
    }

    @JsonIgnore public String getTargetDomain() {
        return dstEndpoint != null ? dstEndpoint.getDomain() : null;
    }
    public void setTargetDomain(String domain) {
        ensureDstEndpoint();
        dstEndpoint.setDomain(domain);
    }

    @JsonIgnore public String getTargetFileHash() {
        if (process == null || process.getFile() == null) return null;
        List<OcsfFileHash> h = process.getFile().getHashes();
        return (h != null && !h.isEmpty()) ? h.get(0).getValue() : null;
    }
    public void setTargetFileHash(String hash) {
        if (hash == null) return;
        ensureProcess();
        ensureProcessFile();
        process.getFile().setHashes(List.of(
                OcsfFileHash.builder().algorithmId(3).algorithm("SHA-256").value(hash).build()
        ));
    }

    @JsonIgnore public String getTargetProcess() {
        return process != null ? process.getName() : null;
    }
    public void setTargetProcess(String name) {
        ensureProcess();
        process.setName(name);
    }

    private void ensureFinding()     { if (finding == null)     finding     = new OcsfFinding(); }
    private void ensureDstEndpoint() { if (dstEndpoint == null) dstEndpoint = new OcsfEndpoint(); }
    private void ensureProcess()     { if (process == null)     process     = new OcsfProcess(); }
    private void ensureProcessFile() { if (process.getFile() == null) process.setFile(new OcsfFile()); }
}