package com.viettelDigitalTalent.EntitiyManagement.normalize.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfFile;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfFileHash;
import com.viettelDigitalTalent.EntitiyManagement.normalize.ocsf.OcsfProcess;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * OCSF class_uid = 1007 (Process Activity), category_uid = 1 (System Activity).
 * activity_id: 1=Launch  2=Terminate  3=Inject  4=Open
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("PROCESS")
public class ProcessEvent extends BaseEvent {

    @JsonProperty("process") private OcsfProcess process;

    // ── Convenience bridge ────────────────────────────────────────────────────

    @JsonIgnore public String getProcessName() {
        return process != null ? process.getName() : null;
    }
    public void setProcessName(String name) {
        ensureProcess();
        process.setName(name);
    }

    @JsonIgnore public String getProcessPath() {
        return process != null && process.getFile() != null ? process.getFile().getPath() : null;
    }
    public void setProcessPath(String path) {
        ensureProcess();
        ensureFile();
        process.getFile().setPath(path);
        if (process.getFile().getName() == null && path != null) {
            int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            process.getFile().setName(idx >= 0 ? path.substring(idx + 1) : path);
        }
    }

    /** Stored as SHA-256 hash (algorithm_id=3) */
    @JsonIgnore public String getFileHash() {
        if (process == null || process.getFile() == null) return null;
        List<OcsfFileHash> h = process.getFile().getHashes();
        if (h == null || h.isEmpty()) return null;
        return h.get(0).getValue();
    }
    public void setFileHash(String hash) {
        if (hash == null) return;
        ensureProcess();
        ensureFile();
        process.getFile().setHashes(List.of(
                OcsfFileHash.builder().algorithmId(3).algorithm("SHA-256").value(hash).build()
        ));
    }

    @JsonIgnore public String getCommandLine() {
        return process != null ? process.getCmdLine() : null;
    }
    public void setCommandLine(String cmd) {
        ensureProcess();
        process.setCmdLine(cmd);
    }

    private void ensureProcess() {
        if (process == null) process = new OcsfProcess();
    }
    private void ensureFile() {
        if (process.getFile() == null) process.setFile(new OcsfFile());
    }
}